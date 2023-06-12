package Sim;

import java.io.*;
import java.util.*;


import ContentFiles.Content;
import Satellites.*;
import Terrestrial.*;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import util.Tuple;

import static Satellites.Satellite.*;
import static Terrestrial.Area.*;
import static Terrestrial.TerrestrialUsers.All_Users;
import static ContentFiles.Content.All_Contents;
import static Terrestrial.Request.All_Requests;

public class Sim {

    private static int total_convert_times =0;
    private static int total_update_times=0;
    private static int request0num=0;
    private static int request1num=0;

    public static void main(String[] args) throws IOException {


            MakeContentFiles();
            MakeUsersInfo();
            ReadRequest();
            init();

            //通过由 实现了Comparable这个接口的类Request构成的链表进行排序，升序
            //Collections.sort(All_Requests);

            //通过升降序比较器来对链表进行排序，注意只能对链表排序，若是对map进行排序，需要将其转换为链表<entry> 来排序
            sortAllRequestbyTime();

            //读取请求、用户、内容信息 并保存至txt文档 各个策略都一样 输出一次即可
            //outputDatasetinfo();

            int time_pointer_convert = 0;
            int time_pointer_update = 0;
            double initial_time_convert = All_Requests.get(time_pointer_convert).getRequest_Time();
            double initial_time_update = All_Requests.get(time_pointer_update).getRequest_Time();
            double current_time;

            for (int i = 0; i < All_Requests.size(); i++) {

                current_time = All_Requests.get(i).getRequest_Time();

                //切换
                if (current_time - initial_time_convert > Time_Interval_convert) {
                    convert();

                    //打印每次切换的地区请求信息 请求了哪些内容 各个缓存策略都一样
                    //printAreaRequestInfoEveryConvert();

                    handover();

                    for(Satellite s:All_Satellites){
                        s.Cached_ContentsID=s.getCachedContentID();
                    }

                    //打印每次切换的地区服务的卫星及其缓存情况
                    printCacheChangeInfo();

                    total_convert_times++;
                    time_pointer_convert = i;
                    initial_time_convert = All_Requests.get(time_pointer_convert).getRequest_Time();
                }

                //更新
                if (current_time - initial_time_update > Time_Interval_update) {

                    //打印地区特征 各个缓存策略都一样
                    //printAreaFeature(total_update_times);

                    time_pointer_update = i;
                    initial_time_update = All_Requests.get(time_pointer_update).getRequest_Time();
                    update();
                    total_update_times++;

                    //打印每次更新后 每个地区的 缓存命中情况
                    printAreaCacheHitRateInfo();

                    for(Area a:All_Areas.values()){
                        a.Hitted_Request_Times=0;
                        a.Hitted_Request_Times_Neighbor=0;
                        a.Request_Times=0;
                    }

                    //打印区域共同缓存的内容数目
                    printSameCachedContents();
                }
                SendRequest(i);
            }
            //记录最后一次更新后的请求表
            for (Area a : All_Areas.values()) {
                List<Map.Entry<Content, Integer>> templist = sortDesendMapValueCompartor(a.Request_Records_EachUpdate.get(total_update_times));
                a.Request_Records_EachUpdate_Sorted.put(total_update_times, templist);

            }

            //打印各个地区的特征向量 各个缓存策略都一样
            //printAreaFeature(total_update_times);

            //打印每个地区的请求情况 各个缓存策略都一样
            //printAreaRequestsInfo();

            //打印每个地区 每次更新周期的请求情况，python根据这部分的数据进行特征预测
            printAreaRequestContentsInfoEveryAreaEveryUpdate();

            //打印每个区域的用户数目、请求数目信息、内容获取延迟
            //printAreaRequestTimesAreaDelay();

            //打印整体的缓存命中情况
            printCacheHitRateInfo();

            System.out.println("Cache hit ratio: " + getCacheHitInLocalSatelliteRatioTuple());
            System.out.println("Cache hit in neighbor satellite ratio: " + getCacheHitInNeighborSatelliteRatioTuple());
            System.out.println("Cache hit rate: " + getCacheHitRatio());
            System.out.println("Content Retrieve Delay (Each Areas): " + getRetrieveDelay());
            System.out.println("Request Times<0: " + request0num +" First Interval Request Times<10: " +request1num);

    }

    public static List<Map.Entry<Content,Integer>> sortDesendMapValueCompartor(HashMap<Content, Integer> requestrecord){
        Comparator<Map.Entry<Content,Integer>> valueCompartor=new Comparator<Map.Entry<Content, Integer>>() {
            @Override
            public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };
        List<Map.Entry<Content,Integer>> requestrecordlist=new ArrayList<>();
        if(requestrecord==null)
            return requestrecordlist;
        requestrecordlist=new ArrayList<>(requestrecord.entrySet());
        requestrecordlist.sort(valueCompartor);
        return requestrecordlist;
    }

    //初始化每颗卫星和地区以及用户
    public static void init(){
        for(int i = 0; i< Columns; i++){
            for(int j = 0; j< Rows; j++){
                int AreaID=i* Rows +j;
                Satellite sate=new Satellite(AreaID);
                Area area=new Area(AreaID,sate);
                All_Satellites.add(sate);
                All_Areas.put(AreaID,area);
            }
        }
        //初始化每个地区
        for(Map.Entry<Integer,Area> entry: All_Areas.entrySet()){
            Area a=entry.getValue();
            a.init();
        }
        //初始化每个卫星
        for(Satellite s:All_Satellites){
            s.init();

        }

        //初始化每个用户
        for(Map.Entry<Integer,TerrestrialUsers> entry:All_Users.entrySet()){
            TerrestrialUsers u=entry.getValue();
            u.init();
        }
        initCooperativeAreas();
    }

    //初始化协作缓存区域
    public static void initCooperativeAreas(){
        for(Area a:All_Areas.values()){
            a.Local_Cooperative_Areas.add(a);
            a.Cooperative_Areas.add(a);
            a.CooperativeAreasID=a.getAreaID();
            Static_Cooperative_Areas.put(a,a.CooperativeAreasID);
        }
    }

    //更新协作缓存区域
    public static void updateCooperativeAreas(){
        //对上一周期的协作缓存区域置零
        for(Area a:All_Areas.values()){
            //情况每个地区的本地协作缓存区域信息
            a.Local_Cooperative_Areas.clear();
            a.Cooperative_Areas.clear();
            //重新设置全局协作缓存区域信息
            a.CooperativeAreasID=a.getAreaID();
            Static_Cooperative_Areas.put(a,a.CooperativeAreasID);
        }

        //得到按请求总数降序排序的区域列表
        HashMap<Area,Integer> AreaRequestmap=new HashMap<>();
        for(Area a:All_Areas.values()){
            AreaRequestmap.put(a,a.getRequestedContents().size());
        }
        Comparator<Map.Entry<Area,Integer>> valueCompartor=new Comparator<Map.Entry<Area, Integer>>() {
            @Override
            public int compare(Map.Entry<Area, Integer> o1, Map.Entry<Area, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };
        List<Map.Entry<Area,Integer>> AreaRequestmaptolist=new ArrayList<>(AreaRequestmap.entrySet());
        AreaRequestmaptolist.sort(valueCompartor);

        //划分协作缓存区域
        for(Map.Entry<Area,Integer> ent:AreaRequestmaptolist){
            /*if(total_update_times==1&&ent.getKey().getAreaID()==8){
                System.out.println("s");
            }*/
            ent.getKey().setCooperative_Areas();
        }

        //每颗卫星获取自己的协作缓存区域邻居
        for(Area a:All_Areas.values()) {
            for (Map.Entry<Area, Integer> ent : Static_Cooperative_Areas.entrySet()) {
                Area b=ent.getKey();
                int distance=getTwoAreaDistance(a,b);
                if(a.CooperativeAreasID==ent.getKey().CooperativeAreasID&&distance<=K){
                    a.Local_Cooperative_Areas.add(b);
                }
                if(a.CooperativeAreasID==ent.getKey().CooperativeAreasID){
                    a.Cooperative_Areas.add(b);
                }
            }
        }


        //协作缓存区域划分完后 对每个区域的标志位进行处理
        for(Area a:All_Areas.values()){
            a.CooperativeFlag=0;
            a.CooperativeAreasID=a.getAreaID();
        }
    }

    //打印相同缓存
    public static void printSameCachedContents(){
        try {
            FileWriter f1=new FileWriter("SameCachedContent.txt",true);
            for(Area a:All_Areas.values()){
                HashMap<Area,Integer> samecache=new HashMap<>();
                for(Area b:All_Areas.values()){
                    int[] acache=a.getServingSatellite().getCachedContentID();
                    int[] bcache=b.getServingSatellite().getCachedContentID();
                    int samenumbers=caculateTwoArraySameItems(acache,bcache);
                    samecache.put(b,samenumbers);
                }
                Comparator<Map.Entry<Area,Integer>> keyCompart=new Comparator<Map.Entry<Area, Integer>>() {
                    @Override
                    public int compare(Map.Entry<Area, Integer> o1, Map.Entry<Area, Integer> o2) {
                        return o1.getKey().getAreaID()-o2.getKey().getAreaID();
                    }
                };
                List<Map.Entry<Area,Integer>> samecachelist=new ArrayList<>(samecache.entrySet());
                samecachelist.sort(keyCompart);
                f1.write("Update times: "+total_update_times+"\n");
                for(Map.Entry<Area,Integer> ent:samecachelist){
                    f1.write("AreaID "+a.getAreaID()+" The same content numbers with area: "+ent.getKey().getAreaID()
                            +" is "+ent.getValue()
                            +" !     "+" And it's Local cooperative areas are :"+Arrays.toString(a.getLocalCooperativeAreasID())
                            +" !     "+" And it's cooperative areas are:"+Arrays.toString(a.getCooperativeAreasID())+"\n");
                }
                f1.write("\n");
            }
            f1.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static int caculateTwoArraySameItems(int[] a,int[] b){
        int res=0;
        for(int i=0;i<a.length;i++){
            for(int j=0;j<b.length;j++){
                if(a[i]==b[j])res++;
            }
        }
        return res;

    }

    //切换函数，每隔较短的时间进行卫星和地面之间的切换
    public static void convert(){
        for(Map.Entry<Integer,Area> entry: All_Areas.entrySet()){
            entry.getValue().convert();
        }
        for(Satellite s:All_Satellites){
            s.convert();
        }

    }
    //卫星handover
    public static void handover(){
        for(int i = 0; i< Columns; i++){
            for (int j = Rows -1; j>0; j--){
                int area_id=i* Rows +j;
                int swap_area_id=area_id-1;
                Satellite s1 = null;
                Satellite s2 = null;
                for(Satellite s:All_Satellites){
                    if(s.getServingArea().getAreaID()==area_id)
                        s1=s;
                    if(s.getServingArea().getAreaID()==swap_area_id)
                        s2=s;
                }
                swapSatelliteCache(s1,s2);
            }
        }

    }

    public static Satellite getSatellitefromSatelliteID(int id){
        for (Satellite s:All_Satellites){
            if(s.getSatelliteID()==id)
                return s;
        }
        return null;
    }

    public static void swapSatelliteCache(Satellite s1, Satellite s2){
        //交换缓存
        List<Content> temp1=deepCopy(s1.getCached_Contents());
        List<Content> temp2=deepCopy(s2.getCached_Contents());
        s1.setCached_Contents(temp2);
        s2.setCached_Contents(temp1);

        //交换LRUCache
        HashMap<Content,Integer> templru1= clone(s1.getLRUCache());
        HashMap<Content,Integer> templru2= clone(s2.getLRUCache());
        s1.setLRUCache(templru2);
        s2.setLRUCache(templru1);

        //交换LFUCache
        HashMap<Content,Integer> templfu1= clone(s1.getLFUCache());
        HashMap<Content,Integer> templfu2= clone(s2.getLFUCache());
        s1.setLFUCache(templfu2);
        s2.setLFUCache(templfu1);

/*        HashMap<Content,Integer> tempridge1= clone(s1.getRidgeCache());
        HashMap<Content,Integer> tempridge2= clone(s2.getRidgeCache());
        s1.setRidgeCache(tempridge2);
        s2.setRidgeCache(tempridge1);*/

        //交换FIFOCache
        List<Content> tempfifo1= deepCopy(s1.getFIFOCache());
        List<Content> tempfifo2= deepCopy(s2.getFIFOCache());
        s1.setFIFOCache(tempfifo2);
        s2.setFIFOCache(tempfifo1);
    }

    //更新函数，每隔较长的时间去更新地区的流行度和卫星的缓存
    public static void update(){
        for(Map.Entry<Integer,Area> entry: All_Areas.entrySet()){
            entry.getValue().update();
        }

        //跟新协作缓存区域
        updateCooperativeAreas();

        //卫星的更新函数，其实就是缓存更新，且只有Ridge和Random的缓存算法，其他LRU LFU FIFO的缓存算法在UpdateCache()函数中
        for(Satellite s:All_Satellites){
            s.update();
        }

        for(Satellite s:All_Satellites){
            s.Cached_Contents_Flag=0;
        }
        Average_CooperativeAreas.add(Average_cooperative_numbers);
        Average_cooperative_numbers=0;
    }

    //读取所有内容至All_Contents
    public static void MakeContentFiles(){
        String fileName=".\\src\\Sim\\u.item";
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            String line = reader.readLine();
            while (line != null) {
                int contentid=getContentID(line);
                String contentfeature= getContentFeature(line);
                Content c=new Content(contentid,contentfeature);
                All_Contents.add(c);
                //System.out.println(line);
                line = reader.readLine();
            }
            reader.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    //读取用户信息至All_Users
    public static void MakeUsersInfo() throws IOException {
        String fileName=".\\src\\Sim\\u.user";
        BufferedReader reader;
        reader=new BufferedReader(new FileReader(fileName));
        String line=reader.readLine();
        try{
            while (line!=null){
                int userid=getUserID(line);
                int userzipcode=getUserZipcode(line);
                //识别邮编异常的用户
                if(userzipcode==0){
                    line=reader.readLine();
                    continue;
                }
                int userareaid=ZipcodetoAreaID(userzipcode);

                TerrestrialUsers user=new TerrestrialUsers(userid,userzipcode,userareaid);
                All_Users.put(userid,user);
                line=reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //读取请求信息至All_Requests
    public static void ReadRequest() throws IOException {
        String filename=".\\src\\Sim\\u.data";
        BufferedReader reader;
        reader=new BufferedReader(new FileReader(filename));
        String line=reader.readLine();
        try {
            while(line!=null){
                int requestuserid;
                int requestcontentid;
                int requestrateing;
                int requesttime;
                requestuserid=Integer.parseInt(line.substring(0,line.indexOf("\t")));

                line=line.substring(line.indexOf("\t")+1);
                requestcontentid=Integer.parseInt(line.substring(0,line.indexOf("\t")));

                line=line.substring(line.indexOf("\t")+1);
                requestrateing=Integer.parseInt(line.substring(0,line.indexOf("\t")));

                line=line.substring(line.indexOf("\t")+1);
                requesttime=Integer.parseInt(line.substring(0,line.length()));

                Request r=new Request(requestuserid,requestcontentid,requestrateing,requesttime);
                All_Requests.add(r);
                line=reader.readLine();
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    //发送请求
    public static void SendRequest(int i){

            Request request= All_Requests.get(i);
            int userid=request.getUserID();
            int contentid=request.getContentID();
            //邮编错误的用户,由于其因为邮编错误并未加入All_Users,故找不到该用户，直接跳过
            if(All_Users.get(userid)==null){
                return;
            }
            //发出请求的用户以及请求的内容
            TerrestrialUsers User = All_Users.get(userid);
            Content content= All_Contents.get(contentid);
            //请求内容
            User.RequestContent(content,request);
            //User的相关信息
            String Userinfo="UserID: " + User.getUserId() +
                    "   UserZipcode: " + User.getZipcode() +
                    "   AreaID: " + User.getUserArea().getAreaID()+
                    "   AreaCoord: " + User.getUserArea().getAreaCoord() +
                    "   Serving_Satellite_ID: " + User.getServingSatellite().getSatelliteID() +
                    "   Request Content ID: " + User.getContentID() +
                    "   Retrieve Content Delay: " + User.RetrieveContentDelay() + "ms"+
                    "   User Retrieve Content Average Delay:"+User.getRetrieveAllContentAverageDelay();
            System.out.println(Userinfo);
            //Area的相关信息
            String Areainfo=
                    "AreaID: "+User.getUserArea().getAreaID()+
                    //"  Requested_Content: " + User.getArea().getRequestedContentsID()+
                    //" and Requested_Content_Feature:"+User.getArea().getRequestedContentsFeature()+
                    " and Area Feature:"+User.getUserArea().getAreaFeatureString()+"\n"+
                    //" and Area_Users are: "+ Arrays.toString(User.getArea().getAreaUserID())+"\n"+
                    " and Area request times: "+(User.getUserArea().getRequestedContents().size()+User.getUserArea().getRequestedContentsOneInterval().size())+"\n"+
                    " and Area average response delay is: "+User.getUserArea().getAreaRetrieveDelay()+"\n"+
                    " and Area's Neighbor: "+ User.getUserArea().getNeighborAreasCoord();
            System.out.println(Areainfo);
            //Satellite的相关信息
            String Satelliteinfo=
                    "Satellite"+User.getUserArea().getServingSatellite().getSatelliteID()+
                    " is serving Area: " +User.getUserArea().getAreaID()+"\n"+
                    //" and the area Requested_Content_ID:"+User.getArea().getServingSatellite().getServingAreaRequestedContentsID()+
                    //" and the area Requested_Content_Feature:"+User.getArea().getServingSatellite().getServingAreaRequestedContentsFeature()+
                    //" and satellite CacheHitRatio: "+ User.getArea().getServingSatellite().getCacheHitRatioList()+"\n"+
                    "Convert times: "+ total_convert_times+"\n"+
                    "Update times: "+ total_update_times;
            System.out.println(Satelliteinfo);
            System.out.println("\n");

    }

    //处理数据集
    public static void outputDatasetinfo() throws FileNotFoundException {
        //得到所有内容的文件
        PrintWriter out=new PrintWriter(".\\InitialInfo\\ContentsInfo.txt");
        for (Content contentFile : All_Contents) {
            String temp = contentFile.getContentID() + " " + contentFile.getContentFeature();
            out.println(temp);
        }
        out.close();

        //得到所有用户信息
        PrintWriter out1=new PrintWriter(".\\InitialInfo\\UsersInfo.txt");
        for (TerrestrialUsers user: All_Users.values()) {
            String temp = user.getUserId() + " " + user.getZipcode()+" "+user.getAreaID();
            out1.println(temp);

        }
        out1.close();

        //得到所有请求信息

        PrintWriter out2=new PrintWriter(".\\InitialInfo\\RequestsInfo.txt");
        for (Request r: All_Requests) {
            String temp = r.getUserID() + " " + r.getContentID()+" "+r.getRequest_Time()+" "+r.getContent_Rating();
            out2.println(temp);
        }
        out2.close();
    }

    //按照时间对请求进行排序,另一种方法
    public static void sortAllRequestbyTime(){
       Collections.sort(All_Requests, new Comparator<Request>() {
           @Override
           public int compare(Request o1, Request o2) {
               return o1.getRequest_Time()-o2.getRequest_Time();
           }
       });

    }

    public static int getContentID(String str){
        String Strid=str.substring(0,str.indexOf("|"));
        return Integer.parseInt(Strid);
    }

    public static String getContentFeature(String str){
        String Strtempfeature=str.substring(str.length()-38,str.length());
        String Strfeature="";
        for(int i=0;i<Strtempfeature.length();i++){
            if(Strtempfeature.charAt(i)!='|'){
                Strfeature+=Strtempfeature.charAt(i);
            }
        }
            return Strfeature;
    }

    public static int getUserID(String str){
        String Strid=str.substring(0,str.indexOf("|"));
        return Integer.parseInt(Strid);
    }

    public static int getUserZipcode(String str){
        
        String userzipcodestr=str.substring(str.length()-5,str.length());
        int userzipcode ;
        try {
            userzipcode=Integer.parseInt(userzipcodestr);
        } catch (NumberFormatException e) {
            //e.printStackTrace();
            userzipcode=0;
        }
        return userzipcode;
    }

    //根据用户的邮编得到用户所在的AreaID
/*    public static int ZipcodetoAreaID(int zipcode){
        int AreaCoord_X;
        int AreaCoord_Y;
        if(zipcode<10000){
            AreaCoord_X=0;
            AreaCoord_Y=zipcode/2000;
        }
        else {
            AreaCoord_X=zipcode/10000;
            AreaCoord_Y=(zipcode-AreaCoord_X*10000)/2000;
        }
        return AreaCoord_X*5+AreaCoord_Y;
    }*/

    public static int ZipcodetoAreaID(int zipcode){
        int col_num=100000/ Columns;
        int grid_num=col_num/ Rows;
        int AreaCoord_X;
        int AreaCoord_Y;
        if(zipcode<col_num){
            AreaCoord_X=0;
            AreaCoord_Y=zipcode/grid_num;
        }
        else {
            AreaCoord_X=zipcode/col_num;
            AreaCoord_Y=(zipcode-AreaCoord_X*col_num)/grid_num;
        }
        return AreaCoord_X* Rows +AreaCoord_Y;
    }

    //总体缓存情况
    public static Tuple<Integer,Integer> getCacheHitInLocalSatelliteRatioTuple(){
        double hitrate;
        int hittimes=0;
        int totaltimes=0;
        for (Satellite s:All_Satellites){
            hittimes+=s.getTotalHittedRequestFromLocal();
            totaltimes+=s.getTotalReceivedRequest();
        }
        return new Tuple<>(hittimes,totaltimes);
    }

    public static Tuple<Integer,Integer> getCacheHitInNeighborSatelliteRatioTuple(){
        double hitrate;
        int hittimes=0;
        int totaltimes=0;
        for (Satellite s:All_Satellites){
            hittimes+=s.getTotalHittedRequestFromNeighbor();
            totaltimes+=s.getTotalReceivedRequest();
        }
        return new Tuple<>(hittimes,totaltimes);
    }

    public static double getCacheHitRatio(){
        double hitrate;
        int hittimes=0;
        int totaltimes=0;
        for (Satellite s:All_Satellites){
                hittimes+=s.getTotalHittedRequest();
                //hittimes+=s.getTotalHittedRequestFromNeighbor();
                totaltimes+=s.getTotalReceivedRequest();
        }
        hitrate=(double) hittimes/totaltimes;
        return hitrate;
    }

    //总体获取延迟
    public static double getRetrieveDelay(){
        double delay=0;
        int areawithoutrequestnum=0;
        for(Map.Entry<Integer,Area> entry:All_Areas.entrySet()){
            Area temparea=entry.getValue();
            if(temparea.getAreaRetrieveDelay()==0){
                areawithoutrequestnum++;
                continue;
            }
            delay+=temparea.getAreaRetrieveDelay();
        }
        System.out.println("Area withoutrequests num: "+areawithoutrequestnum);
        delay=delay/(All_Areas.size()-areawithoutrequestnum);
        return delay;
    }

    //打印区域的特征
    public static void printAreaFeature(int i) throws IOException {
        String path0=".\\AreaFeature\\";
        String path1="AreaFeature"+Time_Interval_update+".txt";
        String path=path0+path1;
        FileWriter out=new FileWriter(path,true);
        out.write("\n");
        for(Map.Entry<Integer,Area> entry:All_Areas.entrySet()){
            Area temp=entry.getValue();
            String AreaFeature=" AreaID: "+temp.getAreaID()+" Area Requests: "+temp.getRequestedContents().size()+ " AreaFeature: "+ temp.getAreaFeatureString();
            out.write("Period: "+i+AreaFeature+"\n");
        }
        out.close();
    }



    //打印每个地区的请求信息
    public static void printAreaRequestInfoEveryConvert(){

        try {
            String path0=".\\RequestsEveryConvert\\";
            String path1="AreaRequestEveryConvertInfo.txt";;
            String path=path0+path1;

            FileWriter out=new FileWriter(path,true);

            out.write("Update Time: "+total_update_times+"\n");
            out.write("Convert Time: "+total_convert_times+"\n");
            for(Area a:All_Areas.values()){

                out.write("AreaID: "+a.getAreaID()+" Area Requests Number: "+a.getRequestedContentsOneInterval().size()+"\t"+
                        " Area Retrieve Delay: "+ Arrays.toString(a.getRequestedContentsOneIntervalID()) +"\t"+"\n");

            }
            out.write("\n");
            out.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }


    //打印每个地区每次更新时的请求信息
    public static void printAreaRequestContentsInfo(){
       String path0=".\\AreaRequestContentsInfo\\";
       for(Area a :All_Areas.values()){
           String path1="Area"+a.getAreaID()+".txt";
           try {
               String path=path0+path1;
               FileWriter out=new FileWriter(path);
               out.write("AreaID: "+a.getAreaID()+"\n");
               for(Map.Entry<Integer,List<Map.Entry<Content,Integer>>> ent:a.Request_Records_EachUpdate_Sorted.entrySet()){
                   out.write("Update Times:" +ent.getKey()+" Area Requests Content Number: "+ent.getValue().size()+"\n");
                   for(Map.Entry<Content,Integer> temp:ent.getValue()){
                       out.write("ContentID "+temp.getKey().getContentID()+" Content Feature: "+temp.getKey().getContentFeature()
                       +" Content Requested Times: "+temp.getValue()+"\n");
                   }
                   out.write("\n"+"\n");
               }
               out.close();
           } catch (IOException e){
               e.printStackTrace();
           }
       }
    }
    //打印每个地区的请求信息 用来分析请求情况
    public static void printAreaRequestsInfo(){
        Comparator<Map.Entry<Integer,Integer>> keyComparatorIntegerInteger=new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o1.getKey()-o2.getKey();
            }
        };
        for(Area a :All_Areas.values()) {
            for (Content c : a.Requested_Contents) {
                int Content_ID = c.getContentID();
                if (!a.Requested_Contents_Times.containsKey(Content_ID)) {
                    int new_times = 1;
                    a.Requested_Contents_Times.put(Content_ID, new_times);
                }
                if (a.Requested_Contents_Times.containsKey(Content_ID)) {
                    int old_times = a.Requested_Contents_Times.get(Content_ID);
                    int new_times = old_times + 1;
                    a.Requested_Contents_Times.replace(Content_ID, old_times, new_times);
                }
            }
            for (int i = 1; i <= 1682; i++) {
                if (!a.Requested_Contents_Times.containsKey(i)) {
                    a.Requested_Contents_Times.put(i, 0);
                }
            }
            a.Requested_Contents_Times_List = new ArrayList<>(a.Requested_Contents_Times.entrySet());
            a.Requested_Contents_Times_List.sort(keyComparatorIntegerInteger);
        }
        String path0=".\\AreaRequestsInfo\\";
        for(Area a :All_Areas.values()){
            String path1="Area"+a.getAreaID()+".txt";
            try {
                String path=path0+path1;
                FileWriter out=new FileWriter(path);
                for(Map.Entry<Integer,Integer> ent:a.Requested_Contents_Times_List){
                    out.write(ent.getValue()+"\n");
                }
                out.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    //打印每个地区每次更新的的请求信息 传递给py
    /*public static void printAreaRequestContentsInfoEveryAreaEveryUpdate(){
        String path0=".\\AreaRequestsInfotopy\\AreaEachUpdateRequestContentsInfoAreaNum"+Area_Numbers+"\\"+"UpdateInterval"+Time_Interval_update+"\\";
        for(Area a :All_Areas.values()){
            String path1="Area"+a.getAreaID();

            try {
                Map.Entry<Integer,List<Map.Entry<Content,Integer>>> entTemp=null;
                //某些地区在第一个周期内并没有产生任何请求！
                for(Map.Entry<Integer,List<Map.Entry<Content,Integer>>> ent:a.Request_Records_EachUpdate_Sorted.entrySet()){

                    String path2="_UpdatePeriod"+ent.getKey()+".txt";
                    String path=path0+path1+path2;
                    String pathcopy=".\\ToBeCopy.txt";
                    FileWriter out=new FileWriter(path);

                    //一个地区一个周期内的请求次数小于30,则用该地区上一个周期替代
                    if(ent.getValue().size()<=L_min&&entTemp!=null){
                        ent=entTemp;
                        request0num++;
                    }
                    //这种情况则代表某一个地区第一个周期内的请求次数小于30,则用一个最流行的请求矩阵代替
                    if(ent.getValue().size()<=L_min&&entTemp==null){
                        request1num++;
                        CopyRequest(pathcopy,path);
                        continue;
                    }
                    for(Map.Entry<Content,Integer> temp:ent.getValue()){
                        out.write(temp.getKey().getContentID()+"|"+temp.getKey().getContentFeature()
                                +"|"+temp.getValue()+"\n");
                    }
                    out.close();
                    entTemp=ent;
                }

            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }*/

    public static void printAreaRequestContentsInfoEveryAreaEveryUpdate(){
        String path0=".\\AreaRequestsInfotopy\\AreaEachUpdateRequestContentsInfoAreaNum"+Area_Numbers+"\\"+"UpdateInterval"+Time_Interval_update+"\\";
        for(Area a :All_Areas.values()){
            String path1="Area"+a.getAreaID();
            try {
                Map.Entry<Integer,List<Map.Entry<Content,Integer>>> entTemp=null;
                //某些地区在第一个周期内并没有产生任何请求！
                for(Map.Entry<Integer,List<Map.Entry<Content,Integer>>> ent:a.Request_Records_Sorted.entrySet()){

                    String path2="_UpdatePeriod"+ent.getKey()+".txt";
                    String path=path0+path1+path2;
                    String pathcopy=".\\ToBeCopy.txt";
                    FileWriter out=new FileWriter(path);

                    //一个地区一个周期内的请求次数小于30,则用该地区上一个周期替代
                    if(ent.getValue().size()<=L_min&&entTemp!=null){
                        ent=entTemp;
                        request0num++;
                    }
                    //这种情况则代表某一个地区第一个周期内的请求次数小于30,则用一个最流行的请求矩阵代替
                    if(ent.getValue().size()<=L_min&&entTemp==null){
                        request1num++;
                        CopyRequest(pathcopy,path);
                        continue;
                    }
                    for(Map.Entry<Content,Integer> temp:ent.getValue()){
                        out.write(temp.getKey().getContentID()+"|"+temp.getKey().getContentFeature()
                                +"|"+temp.getValue()+"\n");
                    }
                    out.close();
                    entTemp=ent;
                }

            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void printCacheHitRateInfo(){
        try {
            String path0;
            if(isCooperative){
                path0=".\\CacheHitRateInfo\\Cooperative\\";
            }
            else
                path0=".\\CacheHitRateInfo\\NonCooperative\\";
            String path1="";
            if(CachingPolicyFlag==1)
                path1="LFU";
            else if(CachingPolicyFlag==2)
                path1="LRU";
            else if(CachingPolicyFlag==3)
                path1="FIFO";
            else if(CachingPolicyFlag==4){
                if(isCooperative){
                    if(RidgeGameTheory)
                        path1="RidgeCooperative";
                    else
                        path1="RidgeCooperativeNoGame";
                }
                else
                    path1="RidgeLocal";
            }

            else if(CachingPolicyFlag==5)
                path1="Random";
            else
                path1="MostPopular";
            String path2="_Capacity"+Cache_Capacity+"_HitRateInfo.txt";
            //String path=path0+path1+path2;
            String path=path0+"CacheSize.txt";
            FileWriter f1=new FileWriter(path,true);
            f1.write("How many cooperative areas: "+ Arrays.toString(Average_CooperativeAreas.toArray()) +"\n");
            f1.write("Average cooperative areas size: "+getAverageCooperativeAreasSize()+"\n");
            f1.write("Caching Policy: "+path1+"\n"
                    + "Caching Capacity: "+Cache_Capacity+"\n"
                    + "Similarity: "+Similarity+"\n"
                    + "Cache hit in local satellite ratio: "+ getCacheHitInLocalSatelliteRatioTuple()+"\n"
                    + "Cache hit in neighbor satellite ratio: "+getCacheHitInNeighborSatelliteRatioTuple()+"\n"
                    + "Cache hit rate: "+getCacheHitRatio() +"\n"
                    + "Content Retrieve Delay (Each Areas): "+getRetrieveDelay()+"\n"+"\n");
          /*  for(Satellite s:All_Satellites){
                String info="Satellite ID: "+s.getSatelliteID();
                for(Map.Entry<Integer,Tuple<Integer,Integer>> ent :s.getCacheHitRatio().entrySet()){
                    String tempinfo=" Serving AreaID: "+ent.getKey()+" Cache Hit Ratio: "+ent.getValue()
                            +" Cache Hit Rate: "+(double)ent.getValue().getKey()/ent.getValue().getValue()
                            +" Serving Area Average Delay: " +s.getAreaFromAreaID(ent.getKey()).getAreaRetrieveDelay();
                    f1.write(info+tempinfo+"\n");
                }
                f1.write("\n");
            }*/
            f1.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void printCacheChangeInfo(){
        try {
            String path0;
            if(isCooperative){
                path0=".\\CacheChangeInfo\\Cooperative\\";
            }
            else
                path0=".\\CacheChangeInfo\\NonCooperative\\";
            String path1="";
            if(CachingPolicyFlag==1)
                path1="LFU";
            else if(CachingPolicyFlag==2)
                path1="LRU";
            else if(CachingPolicyFlag==3)
                path1="FIFO";
            else if(CachingPolicyFlag==4){
                if(isCooperative){
                    if(RidgeGameTheory)
                        path1="RidgeCooperative";
                    else
                        path1="RidgeCooperativeNoGame";
                }

                else
                    path1="RidgeLocal";
            }

            else if(CachingPolicyFlag==5)
                path1="Random";
            else
                path1="MostPopular";
            String path2="_Capacity"+Cache_Capacity+"_ChangeInfo.txt";
            String path=path0+path1+path2;

            FileWriter f1=new FileWriter(path,true);
            String info1="Update Times: "+total_update_times+"\n"+
                    "Convert Times:"+total_convert_times+"\n";
            f1.write(info1);
            for(Area a:All_Areas.values()){
                Satellite s=a.getServingSatellite();
                String info2=" AreaID "+a.getAreaID()+ " Serving Satellite ID "+s.getSatelliteID()+
                        " Cache: "+ Arrays.toString(s.Cached_ContentsID)+"\n";
                f1.write(info2);
            }
            f1.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //打印每个地区在每次更新后的缓存命中情况
    public static void printAreaCacheHitRateInfo(){
        try {
            String path0;
            if(isCooperative){
                path0=".\\CacheHitRatioInfoArea\\Cooperative\\";
            }
            else
                path0=".\\CacheHitRatioInfoArea\\NonCooperative\\";
            String path1="";
            if(CachingPolicyFlag==1)
                path1="LFU";
            else if(CachingPolicyFlag==2)
                path1="LRU";
            else if(CachingPolicyFlag==3)
                path1="FIFO";
            else if(CachingPolicyFlag==4){
                if(isCooperative){
                    if(RidgeGameTheory)
                        path1="RidgeCooperative";
                    else
                        path1="RidgeCooperativeNoGame";
                }
                else
                    path1="RidgeLocal";
            }

            else if(CachingPolicyFlag==5)
                path1="Random";
            else
                path1="MostPopular";
            String path2="_Capacity"+Cache_Capacity+"_AreaCacheHitRateInfo.txt";
            String path=path0+path1+path2;

            FileWriter f1=new FileWriter(path,true);
            String info1="Update Times: "+total_update_times+"\n";
            f1.write(info1);
            for(Area a:All_Areas.values()){
                String info2=" AreaID "+a.getAreaID()
                        + " CacheHitRatio in this interval: "+a.Hitted_Request_Times+" "+a.Hitted_Request_Times_Neighbor
                        +" "+a.Request_Times+" Cache hit rate "+(double)(a.Hitted_Request_Times+a.Hitted_Request_Times_Neighbor)/a.Request_Times
                        +"\n";
                f1.write(info2);
            }
            f1.write("\n");
            f1.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //打印每个地区的请求信息和内容获取延迟
    public static void printAreaRequestTimesAreaDelay(){
        double eachRequestDelay=0;
        try {
            String path0;
            if(isCooperative){
                path0=".\\AreaRequestTimesAreaDelay\\Cooperative\\";
            }
            else
                path0=".\\AreaRequestTimesAreaDelay\\NonCooperative\\";

            String path1="";
            if(CachingPolicyFlag==1)
                path1="LFU";
            else if(CachingPolicyFlag==2)
                path1="LRU";
            else if(CachingPolicyFlag==3)
                path1="FIFO";
            else if(CachingPolicyFlag==4){
                if(isCooperative){
                    if(RidgeGameTheory)
                        path1="RidgeCooperative";
                    else
                        path1="RidgeCooperativeNoGame";
                }
                else
                    path1="RidgeLocal";
            }

            else if(CachingPolicyFlag==5)
                path1="Random";
            else
                path1="MostPopular";
            String path2="_Capacity"+Cache_Capacity+"_AreaRequestTimesRetrievalDelay.txt";
            String path=path0+path1+path2;
            FileWriter out=new FileWriter(path);
            double TotalDely=0;
            int TotalRequests=0;
            for(Area a:All_Areas.values()){
                out.write("AreaID: "+a.getAreaID()+" Area Users: "+a.getAreaUserID().length+" Area Requests Number: "+a.getRequestedContents().size()+"\t"+
                        " Area Retrieve Delay: "+a.getAreaRetrieveDelay()+"\t"+"\n");
                TotalDely+=a.getAreaRetrieveDelay()*a.getRequestedContents().size();
                TotalRequests+=a.getRequestedContents().size();
            }
            eachRequestDelay=TotalDely/TotalRequests;
            out.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    //深拷贝函数
    public static <T> List<T> deepCopy(List<T> src) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            out.writeObject(src);

            ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
            ObjectInputStream in = new ObjectInputStream(byteIn);
            @SuppressWarnings("unchecked")
            List<T> dest = (List<T>) in.readObject();
            return dest;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 使用对象的序列化进而实现深拷贝
     * @param obj
     * @param <T>
     * @return
     */
    public static  <T extends Serializable> T clone(T obj) {
        T cloneObj = null;
        try {
            ByteOutputStream bos = new ByteOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.close();
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            cloneObj = (T) ois.readObject();
            ois.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cloneObj;
    }

    public static void CopyRequest(String pathread,String pathwrite) throws IOException {
        BufferedReader reader=new BufferedReader(new FileReader(pathread));
        BufferedWriter writer=new BufferedWriter(new FileWriter(pathwrite));
        String len="";
        while((len=reader.readLine())!=null){
            writer.write(len);
            writer.flush();
            writer.newLine();
        }
        reader.close();
        writer.close();
    }
    public static int getTwoAreaDistance(Area a, Area b) {
        return Math.abs(a.getAreaCoord().getKey()-b.getAreaCoord().getKey())+Math.abs(a.getAreaCoord().getValue()-b.getAreaCoord().getValue());
    }

    public static double getAverageCooperativeAreasSize(){
        int total=0;
        for(int i=0;i<Average_CooperativeAreas.size();i++){
            total+=Average_CooperativeAreas.get(i);
        }
        double average_cooperative_areas_num=(double) total/Average_CooperativeAreas.size();
        return (double)  Area_Numbers/average_cooperative_areas_num;
    }
}
