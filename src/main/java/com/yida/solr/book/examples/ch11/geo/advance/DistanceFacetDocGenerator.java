package com.yida.solr.book.examples.ch11.geo.advance;

import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.util.NamedList;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 生成用于距离筛选的测试文档。
 * 该类负责创建包含地理位置信息的测试数据文件，并将这些数据索引到Solr中。
 */
public class DistanceFacetDocGenerator {
    private static Random random = new Random();

    /**
     * Solr实例的URL。
     */
    private static final String SOLR_INSTANT_CORE = "http://localhost:8080/solr/distancefacet";
    /**
     * 测试数据文件路径。
     */
    private static final String FILE_PATH = "E:/git-space/solr-book/example-docs/ch11/documents/distancefacet.xml";

    public static void main(String[] args) throws Exception {
        // 生成测试数据文件
        createDataFile();
        // 索引测试数据
        indexData();
    }

    /**
     * 索引测试数据到Solr。
     * @throws Exception 如果索引过程中发生错误。
     */
    private static void indexData() throws Exception {
        HttpSolrClient client = new HttpSolrClient(SOLR_INSTANT_CORE);
        client.setRequestWriter(new BinaryRequestWriter());
        UpdateRequest request = new UpdateRequest();
        request.setAction(UpdateRequest.ACTION.COMMIT, true, true);
        request.setParam("stream.file", FILE_PATH);
        request.setParam("stream.contentType", "application/xml");
        NamedList<Object> result = client.request(request);
        System.out.println("Result: " + result);
    }

    /**
     * 创建包含地理位置信息的测试数据文件。
     */
    private static void createDataFile() {
        List<WeightedLocation> locations = getWeightedLocations();
        OutputStreamWriter writer = null;
        Integer nextDocId = 1;
        try{
            writer = new FileWriter(FILE_PATH);
            writer.write("<add>\n");
            for (WeightedLocation location : locations){
                for (Integer i = 0; i < location.numDocs; i++){
                    StringBuilder doc = new StringBuilder();
                    doc.append("  <doc>\n");
                    doc.append("    <field name=\"id\">" + nextDocId.toString() + "</field>\n");
                    doc.append("    <field name=\"location\">" + changeLastDigit(location.latitude) + "," + changeLastDigit(location.longitude) + "</field>\n");
                    doc.append("    <field name=\"city\">" + location.place + "</field>\n");
                    doc.append("  </doc>\n");
                    writer.write(doc.toString());
                    nextDocId +=1;
                }
            }
            writer.write("</add>");
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally{
            try{
                if (writer != null) {
                    writer.close();
                }
            }
            catch (Exception e){
            }
        }
        System.out.println("Solr documents written to: " + FILE_PATH.toString() + "\n");
        System.out.println("totle documents: " + nextDocId);
    }

    /**
     * 获取加权地理位置列表。
     * 这些地理位置将用于生成测试文档，每个位置有相应的文档数量权重。
     * @return 加权地理位置列表。
     */
    private static List<WeightedLocation> getWeightedLocations(){
        List<WeightedLocation> locations = new ArrayList<WeightedLocation>();
        // 初始化加权地理位置列表，每个元素包含一个地点、经纬度和文档数量。
        locations.add(new WeightedLocation("San Francisco, CA", 37.777,-122.420, 11713));
        locations.add(new WeightedLocation("San Jose, CA", 37.338,-121.886, 3071));
        locations.add(new WeightedLocation("Oakland, CA", 37.805,-122.273, 1482));
        // 其他地点...
        return locations;
    }

    /**
     * 随机修改给定数字的最后一位。
     * 用于创建地理位置的近似值，以增加数据的多样性。
     * @param numberToChange 需要修改的数字。
     * @return 修改后的数字。
     */
    private static Double changeLastDigit(Double numberToChange){
        String newDouble = numberToChange.toString().substring(0, numberToChange.toString().length() -1) + random.nextInt(9);
        return Double.parseDouble(newDouble);
    }

    /**
     * 表示一个具有加权文档数的地理位置。
     */
    static public class WeightedLocation{
        final String place;
        final Double latitude;
        final Double longitude;
        final Integer numDocs;

        /**
         * 构造函数。
         * @param place 地点名称。
         * @param latitude 经度。
         * @param longitude 纬度。
         * @param numDocs 该地点应生成的文档数量。
         */
        public WeightedLocation(String place, Double latitude, Double longitude, Integer numDocs){
            this.place = place;
            this.latitude = latitude;
            this.longitude = longitude;
            this.numDocs = numDocs;
        }
    }
}
