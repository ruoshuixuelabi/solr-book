package com.yida.solr.book.examples.ch07;

import com.yida.solr.book.examples.utils.FileUtils;
import com.yida.solr.book.examples.utils.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.noggit.ObjectBuilder;

import java.io.BufferedReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理UFO目击报告的索引程序。
 */
public class IndexUFO {
    public static Logger log = Logger.getLogger(IndexUFO.class);

    /**
     * Solr服务器的URL。
     */
    private static final String UFO_CORE = "http://localhost:8080/solr/ufo";

    /**
     * 用于解析日期的格式化工具。
     */
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd");
    /**
     * 用于解析月份名称的格式化工具。
     */
    private static final SimpleDateFormat MONTH_NAME_FMT = new SimpleDateFormat("MMMM", Locale.US);

    /**
     * 正则表达式用于匹配美国城市和州的格式。
     */
    private static final Pattern MATCH_US_CITY_AND_STATE = Pattern.compile("^([^,]+),\\s([A-Z]{2})$");

    /**
     * 是否以详细模式运行。
     */
    private boolean beVerbose = false;

    /**
     * 文件操作工具类实例。
     */
    private FileUtils fileUtils;

    /**
     * 构造函数，初始化文件操作工具类。
     */
    public IndexUFO() {
        this.fileUtils = new FileUtils();
    }

    /**
     * 程序入口点。
     *
     * @param args 命令行参数
     * @throws Exception 如果发生错误
     */
    public static void main(String[] args) throws Exception {
        String serverUrl = "http://localhost:8080/solr/ufo";
        int batchSize = 5000;
        String jsonFilePath = "E:/git-space/solr-book/example-docs/ch07/documents/ufo_awesome.json";

        IndexUFO index = new IndexUFO();
        index.importData(serverUrl, batchSize, jsonFilePath);
    }

    /**
     * 从JSON文件导入UFO目击报告数据到Solr索引。
     *
     * @param serverUrl     Solr服务器URL
     * @param batchSize      每次批量提交的文档数
     * @param jsonFilePath  JSON格式的目击报告文件路径
     * @throws Exception 如果发生错误
     */
    public void importData(String serverUrl, int batchSize, String jsonFilePath) throws Exception {
        long startMs = System.currentTimeMillis();

        SolrClient solrClient = new ConcurrentUpdateSolrServer(serverUrl, batchSize, 1);

        int numSent = 0;
        int numSkipped = 0;
        int lineNum = 0;
        SolrInputDocument doc = null;
        String line = null;

        BufferedReader reader = new BufferedReader(this.fileUtils.readFileByPath(jsonFilePath));
        this.fileUtils.rememberCloseable(reader);

        // 逐行处理JSON文件
        while ((line = reader.readLine()) != null) {
            doc = parseNextDoc(line, ++lineNum);
            if (doc != null) {
                solrClient.add(doc);
                ++numSent;
            } else {
                ++numSkipped;
                continue;
            }

            if (lineNum % 5000 == 0) {
                log.info(String.format("Processed %d lines.", lineNum));
            }
        }

        // 添加一个虚构的目击报告，用于测试多值字段的高亮显示
        solrClient.add(createFictitiousSightingWithMultiValuedField());

        // 提交并关闭SolrClient
        solrClient.commit(true, true);
        solrClient.shutdown();

        // 输出导入结果
        float tookSecs = Math.round(((System.currentTimeMillis() - startMs) / 1000f) * 100f) / 100f;
        log.info(String.format("Sent %d sightings (skipped %d) took %f seconds", numSent, numSkipped, tookSecs));
    }

    /**
     * 解析JSON字符串并转换为SolrInputDocument。
     *
     * @param line         JSON字符串
     * @param lineNum      当前行号
     * @return SolrInputDocument对象或null（如果解析失败或数据不完整）
     */
    protected SolrInputDocument parseNextDoc(String line, int lineNum) {
        Map jsonObj = null;
        try {
            jsonObj = (Map) ObjectBuilder.fromJSON(line);
        } catch (Exception jsonErr) {
            if (beVerbose) {
                log.warn("Skipped invalid sighting at line " + lineNum +
                        "; Failed to parse [" + line + "] into JSON due to: " + jsonErr);
            }
            return null;
        }

        // 提取并验证必要字段
        String sighted_at = readField(jsonObj, "sighted_at");
        String location = readField(jsonObj, "location");
        String description = readField(jsonObj, "description");
        if (sighted_at == null || location == null || description == null) {
            if (beVerbose) {
                log.warn("Skipped incomplete sighting at line " + lineNum + "; " + line);
            }
            return null;
        }

        // 解析日期字段
        Date sighted_at_dt;
        try {
            sighted_at_dt = DATE_FORMATTER.parse(sighted_at);
        } catch (ParseException pe) {
            if (beVerbose) {
                log.warn("Skipped sighting at line " + lineNum +
                        " due to invalid sighted_at date (" + sighted_at + ") caused by: " + pe);
            }
            return null;
        }

        // 解析地点字段，提取城市和州
        Matcher matcher = MATCH_US_CITY_AND_STATE.matcher(location);
        if (!matcher.matches()) {
            if (beVerbose) {
                log.warn("Skipped sighting at line " + lineNum +
                        " because location [" + location + "] does not look like a US city and state.");
            }
            return null;
        }
        String city = matcher.group(1);
        String state = matcher.group(2);

        // 清理和处理描述字段
        description = description.replace("&quot;", "\"").replace("&amp;", "&").replace("&apos;", "'");
        description = description.replaceAll("\\s+", " "); // 把多个空格压缩为一个
        description = description.replaceAll("([a-z])([\\.\\?!,;])([A-Z])", "$1$2 $3"); // 在句子末尾和大写字母间添加空格
        description = description.replaceAll("([a-z])([A-Z])", "$1 $2"); // 在单词间添加空格

        // 处理其他字段，并生成文档ID
        String reported_at = readField(jsonObj, "reported_at");
        String shape = readField(jsonObj, "shape");
        String duration = readField(jsonObj, "duration");
        String docId = String.format("%s/%s/%s/%s/%s/%s",
                sighted_at,
                (reported_at != null ? reported_at : "?"),
                city.replaceAll("\\s+", ""),
                state,
                (shape != null ? shape : "?"),
                StringUtils.getMD5Hash(description)).toLowerCase();

        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", docId);
        doc.setField("sighted_at_dt", sighted_at_dt);
        doc.setField("month_s", MONTH_NAME_FMT.format(sighted_at_dt));

        if (reported_at != null) {
            try {
                doc.setField("reported_at_dt", DATE_FORMATTER.parse(reported_at));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        doc.setField("city_s", city);
        doc.setField("state_s", state);
        doc.setField("location_s", location);

        if (shape != null) {
            doc.setField("shape_s", shape);
        }

        if (duration != null) {
            doc.setField("duration_s", duration);
        }
        doc.setField("sighting_en", description);

        return doc;
    }

    /**
     * 从JSON对象中读取指定字段的值。
     *
     * @param jsonObj JSON对象
     * @param key     字段名
     * @return 字段值或null（如果字段不存在或空）
     */
    protected String readField(Map jsonObj, String key) {
        String val = (String) jsonObj.get(key);
        if (val != null) {
            val = val.trim();
            if (val.length() == 0) {
                val = null;
            }
        }
        return val;
    }

    /**
     * 创建一个具有多值字段的虚构UFO目击报告文档。
     *
     * @return 包含多值字段的SolrInputDocument
     * @throws ParseException 如果日期解析失败
     */
    protected SolrInputDocument createFictitiousSightingWithMultiValuedField() throws ParseException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", "sia-fictitious-sighting");
        doc.setField("sighted_at_dt", DATE_FORMATTER.parse("20130401"));
        doc.setField("month_s", "April");
        doc.setField("reported_at_dt", DATE_FORMATTER.parse("20130401"));
        doc.setField("city_s", "Denver");
        doc.setField("state_s", "CO");
        doc.setField("location_s", "Denver, CO");
        doc.setField("shape_s", "unicorn");
        doc.setField("duration_s", "5 seconds");
        doc.setField("sighting_en", "This is a fictitious UFO sighting.");
        doc.addField("nearby_objects_en", "arc of red fire");
        doc.addField("nearby_objects_en", "cluster of dark clouds");
        doc.addField("nearby_objects_en", "thunder and lightning");
        return doc;
    }
}
