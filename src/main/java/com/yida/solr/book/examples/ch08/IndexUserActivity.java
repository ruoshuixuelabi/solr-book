package com.yida.solr.book.examples.ch08;

import com.yida.solr.book.examples.utils.FileUtils;
import com.yida.solr.book.examples.utils.JSONUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.common.SolrInputDocument;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户活动索引类，负责将用户活动数据导入到Solr的solrpedia_instant核心中。
 */
public class IndexUserActivity {
    public static Logger log = Logger.getLogger(IndexUserActivity.class);
    private static final String SOLRPEDIA_INSTANT_CORE = "http://localhost:8080/solr/solrpedia_instant";
    private static final String JSON_FILE_PATH = "E:/git-space/solr-book/example-docs/ch08/documents/solrpedia_instant.json";
    private FileUtils fileUtils;

    /**
     * IndexUserActivity的构造函数，初始化FileUtils实例。
     */
    public IndexUserActivity() {
        this.fileUtils = new FileUtils();
    }

    /**
     * 程序入口点，用于导入数据到Solr索引。
     * @param args 命令行参数
     * @throws Exception 如果数据导入过程中发生错误
     */
    public static void main(String[] args) throws Exception {
        int batchSize = 5000;
        IndexUserActivity index = new IndexUserActivity();
        index.importData(SOLRPEDIA_INSTANT_CORE, batchSize, JSON_FILE_PATH);
    }

    /**
     * 导入数据到Solr索引。
     * @param serverUrl Solr服务器的URL
     * @param batchSize 批量处理的文档数量
     * @param jsonFilePath 用户活动数据的JSON文件路径
     * @throws Exception 如果数据处理或索引过程中发生错误
     */
    public void importData(String serverUrl,int batchSize,String jsonFilePath) throws Exception {
        long startMs = System.currentTimeMillis();
        // 创建Solr客户端实例，使用ConcurrentUpdateSolrServer以支持并发更新
        SolrClient solrClient = new ConcurrentUpdateSolrServer(serverUrl, batchSize, 1);
        // 从文件中读取JSON数据
        String json = new FileUtils().readFileContent(jsonFilePath);
        // 将JSON数据解析为Map列表
        List<Map<String,Object>> list = JSONUtils.fromJson(json,List.class);
        // 遍历列表，为每个文档创建SolrInputDocument并添加到Solr客户端
        for(Map<String,Object> map : list) {
            SolrInputDocument doc = new SolrInputDocument();
            for(Map.Entry<String,Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                // 对于特定字段，如last_executed_on和popularity，进行类型转换
                if("last_executed_on".equals(key)) {
                    Date date = toDate(value.toString());
                    doc.setField(key, date);
                } else if("popularity".equals(key)) {
                    doc.setField(key, Float.valueOf(value.toString()));
                } else {
                    doc.setField(key, value);
                }
            }
            solrClient.add(doc);
        }
        // 提交所有更改到Solr索引
        solrClient.commit(true, true);
        // 关闭Solr客户端连接
        solrClient.shutdown();
        // 计算并记录导入数据所花费的时间
        float tookSecs = Math.round(((System.currentTimeMillis() - startMs) / 1000f) * 100f) / 100f;
        log.info(String.format("import data had took %f seconds", tookSecs));
    }

    /**
     * 将字符串格式的日期转换为Date对象。
     * @param dateStr 日期字符串，格式为yyyy-MM-dd'T'HH:mm:ss
     * @return 转换后的Date对象，如果转换失败则返回null
     */
    private Date toDate(String dateStr) {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        try {
            return format.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }
}
