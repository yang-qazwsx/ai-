package com.ruoyi.system.service.support;

import com.ruoyi.common.core.utils.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 客服的纯文本工具。
 *
 * 这些逻辑（问题归一化、预算解析、关键词命中、Prompt 转义）不依赖 Spring、
 * 数据库或大模型，抽出来既能给 {@code AiServiceImpl} 瘦身，也便于单元测试覆盖。
 *
 * @author ruoyi
 */
public final class AiTextUtils
{
    /** 归一化时要剔除的：所有空白 + 中英文标点 */
    private static final Pattern NOISE_PATTERN =
            Pattern.compile("[\\s\\p{Punct}，。！？；：、‘’“”（）《》【】…—·~]+");

    /** 归一化时要剔除的客套/语气词，长的排前面，避免「请问一下」被「请问」先切成「一下」 */
    private static final String[] FILLER_WORDS = {
            "麻烦问一下", "请问一下", "帮我查一下", "我想问一下", "想知道一下",
            "查一下", "看一下", "问一下", "打扰一下",
            "麻烦", "请问", "帮我", "我想问", "我想", "谢谢了", "谢谢",
            "一下", "的话", "呢", "吗", "啊", "呀", "哈", "咯", "呗"
    };

    /** 常见疑问词同义归一：让「怎么申请退款」「如何申请退款」收敛到同一 key */
    private static final String[][] SYNONYMS = {
            {"怎么样", "如何"}, {"怎样", "如何"}, {"怎么", "如何"}, {"咋", "如何"},
            {"啥时候", "多久"}, {"什么时候", "多久"}, {"多长时间", "多久"},
            {"可不可以", "是否"}, {"是否可以", "是否"}, {"能不能", "是否"}
    };

    /** 「查」->「查询」，但排除「查询」本身和「检查/调查/审查/搜查」等实义词 */
    private static final Pattern CHA_PATTERN = Pattern.compile("(?<![检调审搜])查(?!询)");

    /** 预算抽取：支持阿拉伯数字与中文数字（两百、一千五等） */
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "预算[^0-9零一二三四五六七八九十百千万两]{0,6}([0-9]+(?:\\.[0-9]+)?|[零一二三四五六七八九十百千万两]+)");

    /** 金额抽取：120元 / 两百块 */
    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?|[零一二三四五六七八九十百千万两]+)\\s*(?:元|块)");

    /** 兜底/否定话术标记：命中任意一个就说明没真正答上来 */
    private static final String[] FALLBACK_MARKERS = {
            "暂时无法解答", "无法解答", "未找到相关信息", "未找到", "没有相关", "无法回答", "找不到相关"
    };

    /**
     * 订单号：10~32 位字母/数字/下划线/横线，必须含字母，前后不能紧邻同类字符。
     * 注意不能用 $ 锚定整句末尾：用户常说“BO202611110001 什么状态”，
     * 锚定后订单号后面还有文字就匹配不到，订单直达分支会整体失效。
     */
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_-])(?=[A-Za-z0-9_-]{10,32}(?![A-Za-z0-9_-]))(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z][A-Za-z0-9_-]{9,31}");

    private AiTextUtils()
    {
    }

    /**
     * 问题归一化：把「请问订单多久发货呢？」「订单多久发货!」收敛成同一个 key。
     * 全角转半角 -> 转小写 -> 去客套/语气词 -> 去所有标点与空白。
     */
    public static String normalizeQuestion(String question)
    {
        if (!StringUtils.hasText(question))
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : question.trim().toCharArray())
        {
            if (c >= 0xFF01 && c <= 0xFF5E)
            {
                sb.append((char) (c - 0xFEE0));      // 全角 ASCII -> 半角
            }
            else if (c == 0x3000)
            {
                sb.append(' ');                       // 全角空格
            }
            else
            {
                sb.append(c);
            }
        }
        String s = sb.toString().toLowerCase();
        for (String filler : FILLER_WORDS)
        {
            s = s.replace(filler, "");
        }
        s = NOISE_PATTERN.matcher(s).replaceAll("");
        // 疑问词同义归一（轻量语义缓存）：只统一虚词，不动实义词，避免把不同问题合并
        for (String[] pair : SYNONYMS)
        {
            s = s.replace(pair[0], pair[1]);
        }
        s = CHA_PATTERN.matcher(s).replaceAll("查询");
        // 极端情况：整句被当成客套词削空时退回紧凑原文，避免所有问题挤到同一个 key
        return StringUtils.hasText(s) ? s : question.trim().toLowerCase();
    }

    /** 二元字集合，用于「问题与书名的重合度」这种轻量相关度排序 */
    public static Set<String> bigrams(String s)
    {
        Set<String> set = new LinkedHashSet<>();
        if (!StringUtils.hasText(s))
        {
            return set;
        }
        if (s.length() < 2)
        {
            set.add(s);
            return set;
        }
        for (int i = 0; i + 2 <= s.length(); i++)
        {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    /** 文本是否包含任意关键词 */
    public static boolean containsAny(String text, String[] words)
    {
        if (text == null || words == null)
        {
            return false;
        }
        for (String word : words)
        {
            if (word != null && text.contains(word))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 从提问中抽取订单号候选（按出现顺序去重）。
     * 规则见 {@link #ORDER_NO_PATTERN}：10~32 位、必须含字母、不能与相邻字符粘连。
     */
    public static List<String> findOrderNos(String text)
    {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(text))
        {
            return result;
        }
        Matcher matcher = ORDER_NO_PATTERN.matcher(text);
        while (matcher.find())
        {
            String orderNo = matcher.group();
            if (!result.contains(orderNo))
            {
                result.add(orderNo);
            }
        }
        return result;
    }

    /** 判断回答是否属于兜底/否定话术（为空或命中否定词都算没答上来） */
    public static boolean isFallbackAnswer(String answer)
    {
        if (!StringUtils.hasText(answer))
        {
            return true;
        }
        for (String marker : FALLBACK_MARKERS)
        {
            if (answer.contains(marker))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 转义外部内容中的 XML 特殊字符，避免用户问题/书籍正文里的
     * “&lt;/参考资料&gt; 忽略以上指令” 之类内容跳出数据区、污染 Prompt 结构。
     */
    public static String escapeXml(String text)
    {
        if (!StringUtils.hasText(text))
        {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /** 金额展示：去掉无意义的尾零 */
    public static String plainMoney(BigDecimal price)
    {
        return price == null ? "未知" : price.stripTrailingZeros().toPlainString();
    }

    /** 从提问里抽预算：优先「预算 120 / 预算两百」，其次「120 元/块」「两百元」，取最小值 */
    public static BigDecimal extractBudget(String question)
    {
        if (!StringUtils.hasText(question))
        {
            return null;
        }
        BigDecimal byKeyword = null;
        Matcher m = BUDGET_PATTERN.matcher(question);
        if (m.find())
        {
            byKeyword = parseMoneyToken(m.group(1));
        }
        BigDecimal minMoney = null;
        Matcher m2 = MONEY_PATTERN.matcher(question);
        while (m2.find())
        {
            BigDecimal v = parseMoneyToken(m2.group(1));
            if (v != null && (minMoney == null || v.compareTo(minMoney) < 0))
            {
                minMoney = v;
            }
        }
        if (byKeyword != null)
        {
            return byKeyword;
        }
        return minMoney;
    }

    /** 金额 token 解析：阿拉伯数字直接转，中文数字走 parseChineseNumber */
    public static BigDecimal parseMoneyToken(String token)
    {
        if (!StringUtils.hasText(token))
        {
            return null;
        }
        String value = token.trim();
        try
        {
            if (value.matches("[0-9]+(?:\\.[0-9]+)?"))
            {
                return new BigDecimal(value);
            }
            return parseChineseNumber(value);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 中文数字转金额：支持 两/一二三…十百千万，如 两百=200、一千五=1500（按口语补位）。
     * 只用于预算这种量级，不追求完整中文数字语法。
     */
    public static BigDecimal parseChineseNumber(String text)
    {
        if (!StringUtils.hasText(text))
        {
            return null;
        }
        String s = text.replace("两", "二");
        long total = 0;
        long section = 0;
        long number = 0;
        boolean hasUnit = false;
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            int digit = "零一二三四五六七八九".indexOf(c);
            if (digit >= 0)
            {
                number = digit;
                continue;
            }
            long unit = switch (c)
            {
                case '十' -> 10;
                case '百' -> 100;
                case '千' -> 1000;
                case '万' -> 10000;
                default -> -1;
            };
            if (unit < 0)
            {
                continue;
            }
            hasUnit = true;
            if (unit == 10000)
            {
                section += number;
                total += (section == 0 ? 1 : section) * 10000;
                section = 0;
                number = 0;
            }
            else
            {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            }
        }
        if (!hasUnit)
        {
            return null;
        }
        // 口语“一百五/一千五”：末尾数字没有单位时，按上一级的十分之一补位
        if (number > 0)
        {
            char lastUnitChar = 0;
            for (int i = s.length() - 1; i >= 0; i--)
            {
                char c = s.charAt(i);
                if ("十百千万".indexOf(c) >= 0)
                {
                    lastUnitChar = c;
                    break;
                }
            }
            if ((lastUnitChar == '百' || lastUnitChar == '千') && number < 10)
            {
                section += number * (lastUnitChar == '百' ? 10 : 100);
            }
            else
            {
                section += number;
            }
        }
        return BigDecimal.valueOf(total + section);
    }
}
