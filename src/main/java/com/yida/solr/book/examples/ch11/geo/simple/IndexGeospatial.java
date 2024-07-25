package com.yida.solr.book.examples.ch11.geo.simple;

import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.util.NamedList;

/**
 * Created by Lanxiaowei
 * 第11章第2节第1部分的测试数据导入
 */
public class IndexGeospatial {
    private static final String SOLR_INSTANT_CORE = "http://localhost:8080/solr/geospatial";
    private static final String XML_FILE_PATH = "E:/git-space/solr-book/example-docs/ch11/documents/geospatial.xml";

    /**
     * 程序入口主方法，用于向Solr索引库中批量添加文档。
     * 使用HttpSolrClient通过HTTP协议与Solr服务器通信，实现文档的上传和提交。
     *
     * @param args 命令行参数，本程序中未使用。
     * @throws Exception 如果Solr请求过程中发生错误，则抛出异常。
     */
    public static void main(String[] args) throws Exception {
        // 创建HttpSolrClient实例，用于与Solr服务器交互
        HttpSolrClient client = new HttpSolrClient(SOLR_INSTANT_CORE);
        // 设置请求writer为BinaryRequestWriter，以二进制方式发送请求
        client.setRequestWriter(new BinaryRequestWriter());
        // 创建UpdateRequest对象，用于构造更新请求
        UpdateRequest request = new UpdateRequest();
        // 设置请求动作为提交，并指定立即提交和优化索引
        request.setAction(UpdateRequest.ACTION.COMMIT, true, true);
        // 设置要上传的文件路径和内容类型
        // 设置文件路径，直接上传到Solr Server
        request.setParam("stream.file", XML_FILE_PATH);
        request.setParam("stream.contentType", "application/xml");
        // 执行更新请求，并获取响应结果
        NamedList<Object> result = client.request(request);
        // 输出响应结果
        System.out.println("Result: " + result);
    }
}