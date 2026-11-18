package com.ruoyi.system.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.BizBook;
import com.ruoyi.system.domain.BizBookChunk;
import com.ruoyi.system.domain.BizBookContent;
import com.ruoyi.system.domain.BookChunkDTO;
import com.ruoyi.system.domain.BookOutlineDTO;
import com.ruoyi.system.mapper.BizBookContentMapper;
import com.ruoyi.system.mapper.BizBookMapper;
import com.ruoyi.system.service.IAiBookContentService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 书籍内容服务实现
 *
 * 处理链路：上传文件 → UTF-8 解码 → 按 500 字滑窗分片（重叠 50，尽量在句号处断开）
 * → 分片落库 biz_book_chunk → 写入书籍正文专用向量库 aa_book_index。
 *
 * 拆分前与知识库/记忆共用 aa_rag_index，schema 只有 knowledgeId/answer，
 * type/bookId 无法 filter，只能拉 100 条回内存按 ID 前缀过滤，书多时互相挤占。
 * 拆分后 bookId/chunkNo 进了 schema，检索用 filterExpression 下推到 Redis。
 *
 * @author ruoyi
 */
@Service
public class AiBookContentServiceImpl implements IAiBookContentService
{
    private static final Logger log = LoggerFactory.getLogger(AiBookContentServiceImpl.class);

    /** 单片最大字数（可配置，见 ai.book.chunk-size） */
    @Value("${ai.book.chunk-size:500}")
    private int chunkSize;

    /** 相邻分片重叠字数，避免句子被切断后语义缺失 */
    @Value("${ai.book.chunk-overlap:50}")
    private int chunkOverlap;

    /** 向量写入批大小 */
    @Value("${ai.book.vector-batch:50}")
    private int vectorBatch;

    /** 书籍正文检索相似度阈值 */
    @Value("${ai.book.search-threshold:0.3}")
    private double bookSearchThreshold;

    /** 支持的文件后缀 */
    private static final Set<String> SUPPORTED_EXT = new HashSet<>(Arrays.asList("txt", "md", "markdown", "pdf"));
    /** 章节标题：第X章/节/篇，或 Markdown 标题 */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^(第[一二三四五六七八九十百零\\d]+[章节篇].*|#{1,4}\\s+.+)$");
    /** 文件名非法字符（保留中文、字母数字、点、横线、下划线） */
    private static final Pattern UNSAFE_NAME = Pattern.compile("[^\\u4e00-\\u9fa5A-Za-z0-9._-]");
    /** 原文件临时后缀：事务提交后才改名为正式文件 */
    private static final String TEMP_SUFFIX = ".uploading";

    @Autowired
    private BizBookContentMapper bookContentMapper;
    @Autowired
    private BizBookMapper bookMapper;
    @Autowired
    @Qualifier("bookVectorStore")
    private VectorStore vectorStore;

    /** 原文件落盘目录：在线浏览（PDF 内嵌预览）依赖它 */
    @Value("${ai.book.storage-dir:./data/book-files}")
    private String storageDir;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizBookContent uploadAndParse(Long bookId, MultipartFile file)
    {
        if (bookId == null)
        {
            throw new ServiceException("图书ID不能为空");
        }
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("上传文件不能为空");
        }
        BizBook book = bookMapper.selectBizBookById(bookId);
        if (book == null)
        {
            throw new ServiceException("图书不存在：" + bookId);
        }

        String originalName = StringUtils.isNotEmpty(file.getOriginalFilename())
                ? file.getOriginalFilename() : "content.txt";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!SUPPORTED_EXT.contains(ext))
        {
            throw new ServiceException("只支持 txt / md / pdf 文件，当前文件类型：" + ext);
        }

        byte[] bytes;
        try
        {
            bytes = file.getBytes();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("读取文件失败：" + e.getMessage(), e);
        }

        // 1. 抽取正文：pdf 走 PDFBox。扫描件常抽不出文字，此时不报错，
        //    降级为「仅可在线预览、AI 不可问答」，否则用户传扫描版教材会直接被拒。
        String text;
        boolean pdfNoText = false;
        if ("pdf".equals(ext))
        {
            text = extractPdfText(bytes).replace("\r\n", "\n").trim();
            pdfNoText = text.isEmpty();
        }
        else
        {
            text = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            if (text.isEmpty())
            {
                throw new ServiceException("文件内容为空");
            }
        }

        BizBookContent existing = bookContentMapper.selectContentByBookId(bookId);
        int oldChunkCount = bookContentMapper.countChunksByBookId(bookId);
        String oldFilePath = existing == null ? null : existing.getFilePath();

        List<BizBookChunk> chunks = pdfNoText ? Collections.emptyList() : splitToChunks(bookId, text);

        // 原文件先写临时文件：磁盘不可写时在「任何 DB 变更之前」失败，
        // 避免出现“库里有分片、却没有任何可预览文件”的半成品状态。
        File tmpFile = writeTempRawFile(bookId, bytes, originalName);
        File finalFile = new File(tmpFile.getPath().substring(0,
                tmpFile.getPath().length() - TEMP_SUFFIX.length()));

        // 1. 清理旧分片（重新上传即全量覆盖）
        bookContentMapper.deleteChunksByBookId(bookId);
        // 2. 分片入库，每 200 条一批，避免单条 SQL 过长
        for (int i = 0; i < chunks.size(); i += 200)
        {
            bookContentMapper.batchInsertChunks(chunks.subList(i, Math.min(i + 200, chunks.size())));
        }

        // 3. 维护内容元信息；filePath 直接落最终路径，临时文件在事务提交后原子改名到位
        BizBookContent content = existing != null ? existing : new BizBookContent();
        content.setBookId(bookId);
        content.setFileName(originalName);
        content.setFileType(ext);
        content.setFilePath(finalFile.getAbsolutePath());
        content.setCharCount(text.length());
        content.setChunkCount(chunks.size());
        if (pdfNoText)
        {
            content.setParseStatus(3);
            content.setParseMsg("PDF 未抽取出文本（可能是扫描件），仅支持在线预览，AI 暂无法据此问答");
        }
        else
        {
            content.setParseStatus(2);
            content.setParseMsg("解析成功：共 " + chunks.size() + " 个片段");
        }
        if (existing != null)
        {
            bookContentMapper.updateContent(content);
        }
        else
        {
            bookContentMapper.insertContent(content);
        }

        // 4. 事务提交后再做不可回滚的外部副作用：换文件 + 写向量。
        //    顺序很关键：DB 先提交，失败最多是“向量/文件暂时不一致”，
        //    可通过重新上传或启动重建修复；反过来则会出现“向量指向不存在的分片”。
        registerAfterCommit(book, chunks, oldChunkCount, tmpFile, finalFile, oldFilePath, content);
        return content;
    }

    @Override
    public BizBookContent getContent(Long bookId)
    {
        return bookContentMapper.selectContentByBookId(bookId);
    }

    @Override
    public BookOutlineDTO getOutline(Long bookId)
    {
        BizBookContent content = bookContentMapper.selectContentByBookId(bookId);
        if (content == null)
        {
            return null;
        }
        BizBook book = bookMapper.selectBizBookById(bookId);
        List<BizBookChunk> outline = bookContentMapper.selectChunkOutline(bookId);
        List<BookOutlineDTO.ChapterDTO> chapters = new ArrayList<>(outline.size());
        for (BizBookChunk chunk : outline)
        {
            BookOutlineDTO.ChapterDTO item = new BookOutlineDTO.ChapterDTO();
            item.setChunkNo(chunk.getChunkNo());
            item.setChapter(StringUtils.isNotEmpty(chunk.getChapter())
                    ? chunk.getChapter() : "第 " + chunk.getChunkNo() + " 页");
            item.setCharCount(chunk.getCharCount());
            chapters.add(item);
        }
        BookOutlineDTO dto = new BookOutlineDTO();
        dto.setBookId(bookId);
        dto.setBookTitle(book == null ? null : book.getTitle());
        dto.setFileName(content.getFileName());
        dto.setFileType(content.getFileType());
        dto.setCharCount(content.getCharCount());
        dto.setChunkCount(content.getChunkCount());
        dto.setParseStatus(content.getParseStatus());
        dto.setParseMsg(content.getParseMsg());
        // PDF 且原文件还在，前端才用 iframe 内嵌预览
        dto.setPreviewable("pdf".equalsIgnoreCase(content.getFileType()) && resolveRawFile(content) != null);
        dto.setChapters(chapters);
        return dto;
    }

    @Override
    public BookChunkDTO readChunk(Long bookId, int chunkNo)
    {
        BizBookChunk chunk = bookContentMapper.selectChunkByNo(bookId, chunkNo);
        if (chunk == null)
        {
            return null;
        }
        BookChunkDTO dto = new BookChunkDTO();
        dto.setBookId(bookId);
        dto.setChunkNo(chunk.getChunkNo());
        dto.setChapter(chunk.getChapter());
        dto.setContent(chunk.getContent());
        dto.setCharCount(chunk.getCharCount());
        dto.setTotal(bookContentMapper.countChunksByBookId(bookId));
        return dto;
    }

    @Override
    public File getRawFile(Long bookId)
    {
        return resolveRawFile(bookContentMapper.selectContentByBookId(bookId));
    }

    /**
     * 解析 PDF 文本；加密或损坏时抛业务异常（提示用户而不是静默返回空）
     */
    private String extractPdfText(byte[] bytes)
    {
        try (PDDocument document = PDDocument.load(bytes))
        {
            if (document.isEncrypted())
            {
                throw new ServiceException("PDF 已加密，无法解析，请上传未加密的文件");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("解析 PDF 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 原文件写入临时文件：{storageDir}/{bookId}/{时间戳}_{安全文件名}.tmp
     * 先写临时文件、事务提交后再改名，保证“DB 有记录”与“文件存在”不会出现半个状态。
     */
    private File writeTempRawFile(Long bookId, byte[] bytes, String originalName)
    {
        String safeName = UNSAFE_NAME.matcher(originalName).replaceAll("_");
        File dir = new File(new File(storageDir), String.valueOf(bookId));
        try
        {
            Files.createDirectories(dir.toPath());
            File target = new File(dir, System.currentTimeMillis() + "_" + safeName + TEMP_SUFFIX);
            Files.write(target.toPath(), bytes);
            return target;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("保存原文件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 注册“事务提交后”动作：临时文件改名到位、删除旧文件、同步向量。
     *
     * 这些动作不可回滚，所以必须放在 DB 提交之后；事务回滚时则清理临时文件，
     * 避免磁盘残留无人引用的垃圾文件。
     */
    private void registerAfterCommit(BizBook book, List<BizBookChunk> chunks, int oldChunkCount,
                                     File tmpFile, File finalFile, String oldFilePath,
                                     BizBookContent content)
    {
        Runnable afterCommit = () -> syncAfterWrite(book, chunks, oldChunkCount, tmpFile, finalFile, oldFilePath, content);
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            // 理论上不会走到这里（方法本身 @Transactional），兼容内部直接调用场景
            afterCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                afterCommit.run();
            }

            @Override
            public void afterCompletion(int status)
            {
                if (status != STATUS_COMMITTED)
                {
                    try
                    {
                        Files.deleteIfExists(tmpFile.toPath());
                    }
                    catch (IOException ignored)
                    {
                        // 临时文件清理失败不影响主流程
                    }
                }
            }
        });
    }

    /** 事务提交后的实际动作：文件改名到位 + 删旧文件 + 同步向量 */
    private void syncAfterWrite(BizBook book, List<BizBookChunk> chunks, int oldChunkCount,
                                File tmpFile, File finalFile, String oldFilePath,
                                BizBookContent content)
    {
        try
        {
            Files.move(tmpFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            log.error("书籍原文件落盘失败：bookId={}，{}", book.getBookId(), e.getMessage(), e);
            markVectorSyncFailed(content, "原文件保存失败，请重新上传");
            return;
        }
        // 新文件到位后再删旧文件，避免中途失败把旧的可预览文件也弄丢
        deleteQuietly(oldFilePath, finalFile.getAbsolutePath());

        try
        {
            if (!chunks.isEmpty())
            {
                addToVectorStore(book, chunks);
            }
            // 分片变少时旧的向量不会被覆盖，必须显式删除
            removeStaleVectors(book.getBookId(), chunks.size(), oldChunkCount);
        }
        catch (Exception e)
        {
            log.error("书籍向量同步失败：bookId={}，{}", book.getBookId(), e.getMessage(), e);
            // 旧向量与新分片不一致，继续用会拿旧正文作答；宁可先清空并提示重新上传
            clearBookVectors(book.getBookId(), Math.max(oldChunkCount, chunks.size()));
            markVectorSyncFailed(content, "分片已入库，但向量同步失败，AI 检索暂不可用，请重新上传");
        }
    }

    /**
     * 删除某本书的全部正文数据：分片、内容元信息、向量、磁盘原文件。
     * DB 在同一事务内删除；向量与磁盘文件在事务提交后清理——
     * 外部资源一旦删除无法随事务回滚，必须等 DB 确定提交成功。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBookContent(Long bookId)
    {
        if (bookId == null)
        {
            return;
        }
        BizBookContent content = bookContentMapper.selectContentByBookId(bookId);
        List<BizBookChunk> chunks = bookContentMapper.selectChunksByBookId(bookId);
        if (content == null && (chunks == null || chunks.isEmpty()))
        {
            return;
        }
        bookContentMapper.deleteChunksByBookId(bookId);
        bookContentMapper.deleteContentByBookId(bookId);

        final BizBookContent finalContent = content;
        final List<String> vectorIds = new ArrayList<>();
        if (chunks != null)
        {
            for (BizBookChunk chunk : chunks)
            {
                vectorIds.add(StringUtils.hasText(chunk.getVectorId())
                        ? chunk.getVectorId()
                        : "book-" + bookId + "-" + chunk.getChunkNo());
            }
        }
        Runnable cleanup = () ->
        {
            if (!vectorIds.isEmpty())
            {
                try
                {
                    vectorStore.delete(vectorIds);
                    log.info("已清理图书向量：bookId={}，{} 条", bookId, vectorIds.size());
                }
                catch (Exception e)
                {
                    log.warn("清理图书向量失败：bookId={}，{}", bookId, e.getMessage());
                }
            }
            if (finalContent != null)
            {
                deleteFileQuietly(finalContent.getFilePath());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    cleanup.run();
                }
            });
        }
        else
        {
            cleanup.run();
        }
        log.info("已删除图书正文：bookId={}，分片={}", bookId, chunks == null ? 0 : chunks.size());
    }

    /** 向量同步失败：回写解析状态，让后台列表能直接看到“需要重新上传” */
    private void markVectorSyncFailed(BizBookContent content, String message)
    {
        content.setParseStatus(3);
        content.setParseMsg(message);
        try
        {
            bookContentMapper.updateContent(content);
        }
        catch (Exception e)
        {
            log.warn("回写解析失败状态时出错：bookId={}，{}", content.getBookId(), e.getMessage());
        }
    }

    /** 清空某本书的全部向量（用于向量同步失败后的兜底，避免继续召回旧正文） */
    private void clearBookVectors(Long bookId, int count)
    {
        if (count <= 0)
        {
            return;
        }
        List<String> ids = new ArrayList<>(count);
        for (int no = 1; no <= count; no++)
        {
            ids.add("book-" + bookId + "-" + no);
        }
        try
        {
            vectorStore.delete(ids);
        }
        catch (Exception e)
        {
            log.warn("清理书籍向量失败：bookId={}，{}", bookId, e.getMessage());
        }
    }

    /** 删除旧原文件；路径为空或与新文件相同则跳过，失败只记日志不影响主流程 */
    private void deleteQuietly(String oldPath, String newPath)
    {
        if (!StringUtils.hasText(oldPath))
        {
            return;
        }
        try
        {
            File old = new File(oldPath).getCanonicalFile();
            File current = new File(newPath).getCanonicalFile();
            if (old.getPath().equals(current.getPath()))
            {
                return;
            }
            if (old.isFile() && old.delete())
            {
                log.info("已删除旧书籍原文件：" + old.getPath());
            }
        }
        catch (IOException e)
        {
            log.info("删除旧书籍原文件失败：" + e.getMessage());
        }
    }

    /**
     * 删除书籍原文件及其空目录。
     * 与读取一样做「规范化路径 + 前缀校验」，避免被污染的 filePath 删到磁盘任意文件。
     */
    private void deleteFileQuietly(String path)
    {
        if (!StringUtils.hasText(path))
        {
            return;
        }
        try
        {
            File root = new File(storageDir).getCanonicalFile();
            File file = new File(path).getCanonicalFile();
            if (!file.getPath().startsWith(root.getPath() + File.separator))
            {
                log.warn("删除书籍文件时路径越界，已跳过：{}", file.getPath());
                return;
            }
            if (file.isFile() && file.delete())
            {
                log.info("已删除书籍原文件：{}", file.getPath());
            }
            File dir = file.getParentFile();
            if (dir != null && dir.getPath().startsWith(root.getPath() + File.separator))
            {
                String[] rest = dir.list();
                if (rest != null && rest.length == 0 && dir.delete())
                {
                    log.info("已删除空的书籍目录：{}", dir.getPath());
                }
            }
        }
        catch (IOException e)
        {
            log.warn("删除书籍文件失败：{}", e.getMessage());
        }
    }

    /**
     * 校验并返回可读的原文件。
     * 必须做「规范化路径 + 前缀校验」，否则 filePath 一旦被污染就能读到磁盘任意文件。
     */
    private File resolveRawFile(BizBookContent content)
    {
        if (content == null || !StringUtils.hasText(content.getFilePath()))
        {
            return null;
        }
        try
        {
            File root = new File(storageDir).getCanonicalFile();
            File file = new File(content.getFilePath()).getCanonicalFile();
            if (!file.getPath().startsWith(root.getPath() + File.separator))
            {
                log.info("书籍原文件路径越界，拒绝访问：" + file.getPath());
                return null;
            }
            return file.isFile() ? file : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /**
     * 删除重新上传后多余的向量：旧分片 5 个、新分片 3 个时，book-x-4/5 会残留，
     * 检索命中它们会拿到已不存在的正文，所以必须按 ID 显式删除。
     */
    private void removeStaleVectors(Long bookId, int newCount, int oldCount)
    {
        if (oldCount <= newCount)
        {
            return;
        }
        List<String> staleIds = new ArrayList<>();
        for (int no = newCount + 1; no <= oldCount; no++)
        {
            staleIds.add("book-" + bookId + "-" + no);
        }
        try
        {
            vectorStore.delete(staleIds);
            log.info("已清理重新上传后的残留向量：" + staleIds.size() + " 条");
        }
        catch (Exception e)
        {
            // 向量清理失败不影响本次上传（正文与分片已正确覆盖），仅提示
            log.info("清理残留向量失败（可忽略）：" + e.getMessage());
        }
    }

    @Override
    public List<Document> searchBookChunks(String question, int topK)
    {
        if (!StringUtils.hasText(question))
        {
            return Collections.emptyList();
        }
        // 独立 aa_book_index 里全是书籍片段，无需再按 ID 前缀排除知识库/记忆。
        // 适度放大 topK 是为了覆盖"多本书竞争"的场景（后续按书分组取前 3 本），
        // 但不再需要原来混索引时的 topK*20/100 那种暴力召回。
        int recallTopK = Math.min(Math.max(topK * 4, 20), 60);
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(recallTopK)
                .similarityThreshold(bookSearchThreshold)
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty())
        {
            return Collections.emptyList();
        }
        // 独立索引后每个 doc 必然是书籍片段，但仍需按书分组：
        // 多本书都上传过正文时，不同书的片段会互相挤占 topK，
        // 模型拿到"半本 A 半本 B"就会答非所问。这里按书分组算相关度，
        // 取前 3 本、每本最多 2 个片段，每段带书名交给模型判断。
        Map<Long, List<Document>> byBook = new LinkedHashMap<>();
        Map<Long, Double> bestScore = new HashMap<>();
        for (Document doc : docs)
        {
            String id = doc.getId();
            if (id == null)
            {
                continue;
            }
            Long bookId = parseBookIdFromDocId(id);
            if (bookId == null)
            {
                continue;
            }
            byBook.computeIfAbsent(bookId, k -> new ArrayList<>()).add(doc);
            double score = doc.getScore() == null ? 0D : doc.getScore();
            bestScore.merge(bookId, score, Math::max);
        }
        if (byBook.isEmpty())
        {
            return Collections.emptyList();
        }
        List<Document> matched = new ArrayList<>();
        // 按书的相关度排序，取前 3 本、每本最多 2 个片段：
        // 只取 1 本会漏（正确内容所在的书记录少、单条得分略低时被另一本挤掉，实测偶发答非所问）；
        // 取前 3 本能覆盖"小书库全量参与竞争"的常见情况，又不至于把rompt塞满。
        List<Map.Entry<Long, Double>> ranked = new ArrayList<>(bestScore.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int maxBooks = Math.min(3, ranked.size());
        int maxChunks = Math.max(6, topK * 2);
        for (int i = 0; i < maxBooks && matched.size() < maxChunks; i++)
        {
            Long bookId = ranked.get(i).getKey();
            BizBook book = bookMapper.selectBizBookById(bookId);
            String title = (book == null || book.getTitle() == null) ? "" : book.getTitle();
            int taken = 0;
            for (Document doc : byBook.get(bookId))
            {
                if (taken >= 2 || matched.size() >= maxChunks)
                {
                    break;
                }
                doc.getMetadata().put("bookTitle", title);
                doc.getMetadata().put("bookId", String.valueOf(bookId));
                matched.add(doc);
                taken++;
            }
        }
        return matched;
    }

    /** 从 book-{bookId}-{chunkNo} 解析 bookId */
    private Long parseBookIdFromDocId(String docId)
    {
        String[] parts = docId.split("-");
        if (parts.length < 3)
        {
            return null;
        }
        try
        {
            return Long.valueOf(parts[1]);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * 按 500 字滑窗分片，尽量在句号处断开。
     * 章节记录「分片起始章节 ~ 分片结束章节」：单个分片可能跨多章，
     * 只记最后一个标题会误导引用来源（例如事实在第二章、却标注成第三章）。
     */
    private List<BizBookChunk> splitToChunks(Long bookId, String text)
    {
        List<BizBookChunk> chunks = new ArrayList<>();
        int len = text.length();
        int start = 0;
        int no = 1;
        List<ChapterMark> marks = collectChapters(text);
        while (start < len)
        {
            int end = Math.min(start + chunkSize, len);
            if (end < len)
            {
                int stop = text.lastIndexOf('。', end);
                // 至少在半个窗口之后才接受句号断点，避免分片过短
                if (stop > start + chunkSize / 2)
                {
                    end = stop + 1;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty())
            {
                BizBookChunk chunk = new BizBookChunk();
                chunk.setBookId(bookId);
                chunk.setChunkNo(no);
                chunk.setChapter(chapterOf(start, end, marks));
                chunk.setContent(piece);
                chunk.setCharCount(piece.length());
                chunk.setVectorId("book-" + bookId + "-" + no);
                chunks.add(chunk);
                no++;
            }
            if (end >= len)
            {
                break;
            }
            // 前移时回退一段做重叠；+1 兜底防止死循环
            start = Math.max(end - chunkOverlap, start + 1);
        }
        return chunks;
    }

    /** 章节标记：标题在全文中的起始偏移 + 标题文本 */
    private static class ChapterMark
    {
        private final int pos;
        private final String title;

        ChapterMark(int pos, String title)
        {
            this.pos = pos;
            this.title = title;
        }
    }

    /** 扫描全文中的章节标题（按出现位置升序） */
    private List<ChapterMark> collectChapters(String text)
    {
        List<ChapterMark> marks = new ArrayList<>();
        int offset = 0;
        for (String line : text.split("\n", -1))
        {
            String t = line.trim();
            if (!t.isEmpty() && t.length() <= 40 && CHAPTER_PATTERN.matcher(t).matches())
            {
                marks.add(new ChapterMark(offset, t.replaceAll("^#+\\s*", "")));
            }
            // +1 补回被 split 掉的换行符，保证 offset 与全文下标对齐
            offset += line.length() + 1;
        }
        return marks;
    }

    /**
     * 计算分片 [start, end) 的章节：
     * 起始章节 = start 之前（含）最后一个标题；若分片内还有后续标题，则记录为「起 ~ 止」。
     */
    private String chapterOf(int start, int end, List<ChapterMark> marks)
    {
        String atStart = null;
        String lastInside = null;
        for (ChapterMark m : marks)
        {
            if (m.pos <= start)
            {
                atStart = m.title;
            }
            if (m.pos < end)
            {
                lastInside = m.title;
            }
            else
            {
                break;
            }
        }
        String first = atStart != null ? atStart : lastInside;
        if (first == null)
        {
            return null;
        }
        if (lastInside != null && !lastInside.equals(first))
        {
            return first + " ~ " + lastInside;
        }
        return first;
    }

    /** 分批写入向量库，metadata 带 type/bookId/bookTitle/chunkNo/chapter */
    private void addToVectorStore(BizBook book, List<BizBookChunk> chunks)
    {
        List<Document> batch = new ArrayList<>(vectorBatch);
        for (BizBookChunk chunk : chunks)
        {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "book");
            metadata.put("bookId", String.valueOf(book.getBookId()));
            metadata.put("bookTitle", book.getTitle() == null ? "" : book.getTitle());
            metadata.put("chunkNo", String.valueOf(chunk.getChunkNo()));
            if (chunk.getChapter() != null)
            {
                metadata.put("chapter", chunk.getChapter());
            }
            String docText = (chunk.getChapter() == null ? "" : chunk.getChapter() + "\n") + chunk.getContent();
            batch.add(new Document(chunk.getVectorId(), docText, metadata));
            if (batch.size() >= vectorBatch)
            {
                vectorStore.add(new ArrayList<>(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty())
        {
            vectorStore.add(batch);
        }
    }
}
