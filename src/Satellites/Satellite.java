package Satellites;
import ContentFiles.Content;
import Terrestrial.Area;
import Terrestrial.Request;
import util.Tuple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.List;

import static Terrestrial.Area.All_Areas;
import static ContentFiles.Content.All_Contents;
public class Satellite implements CalAreaDistance{

    public static final List<Satellite> All_Satellites = new ArrayList<>();
    //卫星切换服务区域的周期 s
    public static final double Time_Interval_convert =1200;
    private final int Satellite_ID;

    public final List<Content> Cached_Contents=new ArrayList<>();
    public int Cached_Contents_Flag=0;
    public int[] Cached_ContentsID=new int[Cache_Capacity];
    //private final List<Integer> Cached_Contents_ID=new ArrayList<>(Cache_Capacity);
    private Area Serving_Area;
    private int Serving_AreaID;
    //private final List<Tuple<Integer,Tuple<Integer,Integer>>> Cache_Hit_Ratio=new ArrayList<>();
    private final HashMap<Integer,Tuple<Integer,Integer>> Cache_Hit_Ratio=new HashMap<>();
    public int Total_Received_Request_One_Interval =0;
    public int Total_Hited_Request_One_Interval =0;
    public int Total_Received_Request=0;
    public int Total_Hited_Request=0;
    public int Total_Hited_Request_From_Neighbor =0;
    private Area.Areafeature Serving_Area_Feature;
    private HashMap<Content,Integer> LRUCache=new HashMap<>();
    private HashMap<Content,Integer> LFUCache=new HashMap<>();
    private HashMap<Content,Integer> RidgeCache=new HashMap<>();
    private List<Content> FIFOCache=new LinkedList<>();
    private static final Random random=new Random(1);
    private static  final Random r=new Random(2);
    public static int Average_cooperative_numbers=0;
    public static List<Integer>  Average_CooperativeAreas=new ArrayList<>();
    /**
     * 星地延迟 τ_TS
     */
    public static final int Delay_Retrieve_Content_From_Satellite =300;
    /**
     * 星星延迟 τ_TS
     */
    public static final int Delay_Retrieve_Content_From_Satellite_Neighbor =130;
    /**
     * 地地延迟 τ_TP
     */
    public static final int Delay_Retrieve_Content_From_Terrestrial =500;

    /**地区划分粒度 N
     */
    public static final int Dim = 4;
    public static final int Columns = Dim;
    public static final int Rows =Dim;
    public static final int Area_Numbers =Columns*Rows;

    /**
     * 一个协作缓存区域大小 为区域总数
     */
    public static final int Cooperative_Areas_Size=Area_Numbers;

    /**更新周期90000 T
     */
    public static final int Time_Interval_update=2400000;

    /**最小请求矩阵的次数 L
     */
    public static final int L_min=10;

    /**
     * 缓存空间 c
     */
    public static final int Cache_Capacity=50;


    /**
     * 在协作缓存情况下，一个卫星的协作缓存区域大小 K 不同于协作缓存区域的大小
     */
    //public static final int K=(Delay_Retrieve_Content_From_Terrestrial-Delay_Retrieve_Content_From_Satellite)/Delay_Retrieve_Content_From_Satellite_Neighbor;
    public static final int K=1;
    /**缓存策略 1=LFU 2=LRU 3=FIFO 4=RIDGE 5=RANDOM 6=MostPopular(MPC)
     */
    public static final int CachingPolicyFlag=4;

    /**相似性 e
     */
    public static final double Similarity =0.5;

    /**最大迭代次数
     */
    public static final int P =5;
    /**
     * true代表进行博弈 false代表未进行博弈
     */
    public static final boolean RidgeGameTheory =true;
    /**
     * 有协作缓存你 和没有协作缓存
     */
    public static final boolean isCooperative=true;








    public Satellite(int areaid){
        this.Satellite_ID=areaid;
    }

    //卫星初始化函数，初始化每个卫星的服务地区同时缓存内容
    public void init(){
        if(CachingPolicyFlag==5){
            updateCacheRANDOM();
            this.Cached_ContentsID=this.getCachedContentID();
        }
        initServingArea();
    }

   /* 卫星更新函数，只在RidgeCache和Random的情况下，才更新
   此前的问题：

    预测的值偏大 但又不流行 问题在这！
    为何不流行：内容的特征并不具有特性，可能及其流行的特征和十分不流行的特征都是同一个类别的内容，

    这样在计算流行度的时候，这些十分不流行的内容由于其与流行内容的特征相似，所以预测的流行度特别高，占据了缓存位置
    需要在定义内容特征时，引入能充分表明流行内容的特征，例如请求次数！

    解决方法：
    不预测所有内容的流行度，仅仅在上一周期请求的内容中预测
    */
    public void update(){
        //协作缓存方式
        if(isCooperative) {
            //这个部分只适用于两个区域的协作缓存区域，且已经证明
            if (CachingPolicyFlag == 4) {
                //本地流行度表 以及协作缓存区域的流行度表
                Comparator<Tuple<Integer, Double>> tuplevaluecomprator = new Comparator<Tuple<Integer, Double>>() {
                    @Override
                    public int compare(Tuple<Integer, Double> o1, Tuple<Integer, Double> o2) {
                        int flag = 0;
                        if (o2.getValue() - o1.getValue() > 0)
                            flag = 1;
                        else if (o2.getValue() - o1.getValue() < 0)
                            flag = -1;
                        return flag;
                    }
                };

                List<Tuple<Integer, Double>> local_area_gain_list = new ArrayList<>();
                if (this.Serving_Area.Local_Cooperative_Areas.size() == 1) {
                    List<Map.Entry<Content, Double>> local_area_popularity_list = this.Serving_Area.Popularity_List;
                    //本地缓存收益表 以及协作缓存区域（邻居）的缓存收益
                    for (Map.Entry<Content, Double> contentDoubleEntry : local_area_popularity_list) {
                        int content_id = contentDoubleEntry.getKey().getContentID();
                        double local_popularity = contentDoubleEntry.getValue();
                        double total_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                        Tuple<Integer, Double> temp = new Tuple<>(content_id, total_gain);
                        local_area_gain_list.add(temp);
                    }
                } else {
                    List<Map.Entry<Content, Double>> local_area_popularity_list = this.Serving_Area.Popularity_List;
                    List<Map.Entry<Content, Double>> cooperative_area_popularity_list = new ArrayList<>();
                    for (int i = 0; i < this.Serving_Area.Local_Cooperative_Areas.size(); i++) {
                        if (this.Serving_Area.Local_Cooperative_Areas.get(i).getAreaID() != this.Serving_AreaID) {
                            Area cooperative_area = this.Serving_Area.Local_Cooperative_Areas.get(i);
                            cooperative_area_popularity_list = cooperative_area.Popularity_List;
                        }
                    }

                    //协作缓存区域的收益表  注：本地区的收益表在开始已经定义过了
                    List<Tuple<Integer, Double>> cooperative_area_gain_list = new ArrayList<>();

                    for (Map.Entry<Content, Double> contentDoubleEntry : local_area_popularity_list) {
                        int content_id = contentDoubleEntry.getKey().getContentID();
                        //内容在本地流行度
                        double local_popularity = contentDoubleEntry.getValue();
                        //内容在邻居的流行度
                        double cooperative_popularity = getListEntryvalue(content_id, cooperative_area_popularity_list);
                        double total_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite)
                                + cooperative_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite - Delay_Retrieve_Content_From_Satellite_Neighbor);
                        Tuple<Integer, Double> temp = new Tuple<>(content_id, total_gain);
                        local_area_gain_list.add(temp);
                    }

                    for (Map.Entry<Content, Double> contentDoubleEntry : cooperative_area_popularity_list) {
                        int content_id = contentDoubleEntry.getKey().getContentID();
                        double local_popularity = contentDoubleEntry.getValue();
                        double cooperative_popularity = getListEntryvalue(content_id, local_area_popularity_list);

                        double total_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite)
                                + cooperative_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite - Delay_Retrieve_Content_From_Satellite_Neighbor);
                        Tuple<Integer, Double> temp = new Tuple<>(content_id, total_gain);
                        cooperative_area_gain_list.add(temp);
                    }


                    local_area_gain_list.sort(tuplevaluecomprator);
                    cooperative_area_gain_list.sort(tuplevaluecomprator);

                    //本地区与另一个区域相同的内容条目
                    List<Tuple<Integer, Double>> same_local_area_gain_list = new ArrayList<>();
                    //本地区的候补内容条目
                    List<Tuple<Integer, Double>> substitute_local_area_gain_list = new ArrayList<>();

                    List<Tuple<Integer, Double>> same_cooperative_area_gain_list = new ArrayList<>();
                    List<Tuple<Integer, Double>> substitute_cooperative_area_gain_list = new ArrayList<>();

                    int same_content_num = 0;
                    for (int i = 0; i < local_area_gain_list.size() && i < Cache_Capacity; i++) {
                        for (int j = 0; j < cooperative_area_gain_list.size() && j < Cache_Capacity; j++) {
                            if (local_area_gain_list.get(i).getKey().equals(cooperative_area_gain_list.get(j).getKey())) {
                                same_content_num++;

                                same_local_area_gain_list.add(local_area_gain_list.get(i));
                                if (local_area_gain_list.size() > Cache_Capacity + same_content_num) {
                                    substitute_local_area_gain_list.add(local_area_gain_list.get(Cache_Capacity + same_content_num));
                                }

                                same_cooperative_area_gain_list.add(cooperative_area_gain_list.get(j));
                                if (cooperative_area_gain_list.size() > Cache_Capacity + same_content_num) {
                                    substitute_cooperative_area_gain_list.add(cooperative_area_gain_list.get(Cache_Capacity + same_content_num));
                                }
                            }
                        }
                    }
                    same_local_area_gain_list.sort(tuplevaluecomprator);
                    same_cooperative_area_gain_list.sort(tuplevaluecomprator);

                    //进行k次迭代
                    for (int k = 0; k < P; k++) {

                        //四个区域的指针
                        int pointer_same_local_area_gain_list = 0;
                        int pointer_substitute_local_area_gain_list = 0;
                        int pointer_same_cooperative_area_gain_list;
                        int pointer_substitute_cooperative_area_gain_list = 0;

                        for (int i = 0; i < same_content_num; i++) {
                            double gain_cache_local_area = 0;
                            double gain_cache_cooperative_area = 0;
                            double gain_cache_both_area = 0;

                            int content_id = same_local_area_gain_list.get(pointer_same_local_area_gain_list).getKey();

                            //获取B区域的指针
                            pointer_same_cooperative_area_gain_list = getPointerofTuplesList(content_id, same_cooperative_area_gain_list);

                            if (substitute_cooperative_area_gain_list.size() > pointer_substitute_cooperative_area_gain_list) {
                                gain_cache_local_area = same_local_area_gain_list.get(pointer_same_local_area_gain_list).getValue()
                                        + substitute_cooperative_area_gain_list.get(pointer_substitute_cooperative_area_gain_list).getValue();
                            } else {
                                gain_cache_local_area = same_local_area_gain_list.get(pointer_same_local_area_gain_list).getValue();
                            }

                            if (substitute_local_area_gain_list.size() > pointer_substitute_local_area_gain_list) {
                                gain_cache_cooperative_area = same_cooperative_area_gain_list.get(pointer_same_cooperative_area_gain_list).getValue()
                                        + substitute_local_area_gain_list.get(pointer_substitute_local_area_gain_list).getValue();
                            } else {
                                gain_cache_cooperative_area = same_cooperative_area_gain_list.get(pointer_same_cooperative_area_gain_list).getValue();
                            }

                            double local_popularity = getListEntryvalue(content_id, local_area_popularity_list);
                            double cooperative_popularity = getListEntryvalue(content_id, cooperative_area_popularity_list);

                            gain_cache_both_area = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite)
                                    + cooperative_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);

                            int maxgainway = maxposThreeNum(gain_cache_local_area, gain_cache_cooperative_area, gain_cache_both_area);

                            //缓存在本地的收益最大
                            if (maxgainway == 0) {
                                int pointer = getPointerofTuplesList(content_id, cooperative_area_gain_list);
                                double new_gain = cooperative_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                cooperative_area_gain_list.get(pointer).setValue(new_gain);
                                pointer_substitute_cooperative_area_gain_list++;

                            } else if (maxgainway == 1) {
                                int pointer = getPointerofTuplesList(content_id, local_area_gain_list);
                                double new_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                local_area_gain_list.get(pointer).setValue(new_gain);
                                pointer_substitute_local_area_gain_list++;
                            } else {
                                int pointer1 = getPointerofTuplesList(content_id, cooperative_area_gain_list);
                                double new_gain1 = cooperative_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                cooperative_area_gain_list.get(pointer1).setValue(new_gain1);

                                int pointer2 = getPointerofTuplesList(content_id, local_area_gain_list);
                                double new_gain2 = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                local_area_gain_list.get(pointer2).setValue(new_gain2);
                            }
                            pointer_same_local_area_gain_list++;
                        }

                        //为下一次寻找相同内容条目做前提准备
                        same_content_num = 0;
                        local_area_gain_list.sort(tuplevaluecomprator);
                        cooperative_area_gain_list.sort(tuplevaluecomprator);


                        substitute_local_area_gain_list.clear();
                        substitute_cooperative_area_gain_list.clear();
                        same_local_area_gain_list.clear();
                        same_cooperative_area_gain_list.clear();

                        for (int i = 0; i < local_area_gain_list.size() && i < Cache_Capacity; i++) {
                            for (int j = 0; j < cooperative_area_gain_list.size() && j < Cache_Capacity; j++) {
                                if (local_area_gain_list.get(i).getKey().equals(cooperative_area_gain_list.get(j).getKey())) {
                                    same_content_num++;
                                    same_local_area_gain_list.add(local_area_gain_list.get(i));
                                    if (local_area_gain_list.size() > Cache_Capacity + same_content_num) {
                                        substitute_local_area_gain_list.add(local_area_gain_list.get(Cache_Capacity + same_content_num));
                                    }
                                    same_cooperative_area_gain_list.add(cooperative_area_gain_list.get(j));
                                    if (cooperative_area_gain_list.size() > Cache_Capacity + same_content_num) {
                                        substitute_cooperative_area_gain_list.add(cooperative_area_gain_list.get(Cache_Capacity + same_content_num));
                                    }
                                }
                            }
                        }

                    }

                }
                local_area_gain_list.sort(tuplevaluecomprator);
                Cached_Contents.clear();
                for (int i = 0; i < Cache_Capacity && i < local_area_gain_list.size(); i++) {
                    Content temp = null;
                    for (Content c : All_Contents) {
                        if (c.getContentID() == local_area_gain_list.get(i).getKey())
                            temp = c;
                    }
                    Cached_Contents.add(temp);
                }

                //缓存未满的情况 总共就3次
                if (local_area_gain_list.size() < Cache_Capacity) {
                    System.out.println("Cached contents have not been full ! ");
                    int Files = All_Contents.size();

                    for (int i = 0; i < Cache_Capacity; i++) {
                        int contentid = r.nextInt(Files);
                        if (Cached_Contents.contains(All_Contents.get(contentid))) {
                            i--;
                            continue;
                        }
                        Content temp = All_Contents.get(contentid);
                        Cached_Contents.add(temp);
                    }
                }
                //缓存更新完了，地区的这一个更新周期存放的内容就需要清零
                this.getServingArea().getRequestedContentsOneInterval().clear();
            }
            //多区域的博弈论方法 非合作博弈
            /*if (CachingPolicyFlag == 4) {
                if (this.Cached_Contents_Flag == 0) {
                    Average_cooperative_numbers++;
                    //本地流行度表 以及协作缓存区域的流行度表
                    Comparator<Tuple<Integer, Double>> tuplevaluecomprator = new Comparator<Tuple<Integer, Double>>() {
                        @Override
                        public int compare(Tuple<Integer, Double> o1, Tuple<Integer, Double> o2) {
                            int flag = 0;
                            if (o2.getValue() - o1.getValue() > 0)
                                flag = 1;
                            else if (o2.getValue() - o1.getValue() < 0)
                                flag = -1;
                            return flag;
                        }
                    };
                    List<Area> cooperative_areas = new ArrayList<>(this.Serving_Area.Cooperative_Areas);

                    if (cooperative_areas.size() == 1) {
                        List<Map.Entry<Content, Double>> local_area_popularity_list = this.Serving_Area.Popularity_List;
                        List<Tuple<Integer, Double>> local_area_gain_list = new ArrayList<>();
                        //本地缓存收益表 以及协作缓存区域（邻居）的缓存收益
                        for (Map.Entry<Content, Double> contentDoubleEntry : local_area_popularity_list) {
                            int content_id = contentDoubleEntry.getKey().getContentID();
                            double local_popularity = contentDoubleEntry.getValue();
                            double total_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                            Tuple<Integer, Double> temp = new Tuple<>(content_id, total_gain);
                            local_area_gain_list.add(temp);
                        }
                        this.Serving_Area.Gain_List = local_area_gain_list;
                    } else {
                        for (Area a : cooperative_areas) {
                            List<Tuple<Integer, Double>> tempgainlist = new ArrayList<>();
                            for (Map.Entry<Content, Double> contentidpopularitytuple : a.Popularity_List) {
                                int content_id = contentidpopularitytuple.getKey().getContentID();
                                double content_popularity = contentidpopularitytuple.getValue();
                                double gain = content_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                Tuple<Integer, Double> temp = new Tuple<>(content_id, gain);
                                tempgainlist.add(temp);
                            }
                            for (Area b : a.Local_Cooperative_Areas) {
                                int distance = getTwoAreaDistance(a, b);
                                if (distance == 0)
                                    continue;
                                for (Map.Entry<Content, Double> contentidpopularitytuple : b.Popularity_List) {
                                    int content_id = contentidpopularitytuple.getKey().getContentID();
                                    double content_popularity = contentidpopularitytuple.getValue();
                                    double gain = content_popularity * (Delay_Retrieve_Content_From_Terrestrial
                                            - Delay_Retrieve_Content_From_Satellite
                                            - distance * Delay_Retrieve_Content_From_Satellite_Neighbor);
                                    int pointer = getPointerofTuplesList(content_id, tempgainlist);
                                    //成功在tempgainlist中找到该内容的序号 更新收益
                                    if (pointer != 2000) {
                                        double old_gain = tempgainlist.get(pointer).getValue();
                                        double new_gain = old_gain + gain;
                                        tempgainlist.get(pointer).setValue(new_gain);
                                    }
                                    //在tempgainlist中找不到内容的序号，添加一个新的内容收益
                                    else {
                                        Tuple<Integer, Double> temp = new Tuple<>(content_id, gain);
                                        tempgainlist.add(temp);
                                    }
                                }
                            }
                            a.Gain_List = tempgainlist;
                            a.Gain_List.sort(tuplevaluecomprator);
                        }


                        boolean ischanged = true;
                        int p=0;
                        while (ischanged&&RidgeGameTheory&&p<P) {
                            ischanged = false;
                            for (Area a : cooperative_areas) {
                                for (int i = 0; i < Cache_Capacity && i < a.Gain_List.size(); i++) {
                                    int content_id = a.Gain_List.get(i).getKey();
                                    for (Area b : a.Local_Cooperative_Areas) {
                                        int distance = getTwoAreaDistance(a, b);
                                        if (distance == 0)
                                            continue;
                                        int pointer = getPointerofTuplesList(content_id, b.Gain_List);
                                        if (pointer < Cache_Capacity) {
                                            double old_gain = a.Gain_List.get(i).getValue();
                                            double new_gain = 0;
                                            if (cooperative_areas.size() == 2) {
                                                double local_popularity = getListEntryvalue(content_id, a.Popularity_List);
                                                new_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                            } else {
                                                double local_popularity = getListEntryvalue(content_id, a.Popularity_List);
                                                new_gain = local_popularity * (Delay_Retrieve_Content_From_Terrestrial - Delay_Retrieve_Content_From_Satellite);
                                                for (Area c : a.Local_Cooperative_Areas) {
                                                    if (!c.equals(a) && !c.equals(b)) {
                                                        int pointer_c = getPointerofTuplesList(content_id, c.Gain_List);
                                                        //保证该内容在C中没有
                                                        if (pointer_c >=Cache_Capacity) {
                                                            int distance_ac = getTwoAreaDistance(a, c);
                                                            double cooperative_popularity = getListEntryvalue(content_id, c.Popularity_List);
                                                            new_gain += cooperative_popularity * (Delay_Retrieve_Content_From_Terrestrial
                                                                    - Delay_Retrieve_Content_From_Satellite - distance_ac * Delay_Retrieve_Content_From_Satellite_Neighbor);
                                                        }
                                                    }
                                                }
                                            }
                                            if (old_gain != new_gain) {
                                                a.Gain_List.get(i).setValue(new_gain);
                                                ischanged = true;
                                            }
                                        }
                                    }
                                }
                                a.Gain_List.sort(tuplevaluecomprator);
                            }
                            p++;
                        }
                    }
                    for (Area a : cooperative_areas) {
                        if (a.getServingSatellite().Cached_Contents_Flag == 0) {
                            //这里置1或者不置1 结果都是一样的 说明博弈论方法不论从哪一方开始 最终达到平衡点的结果都是一样的
                            a.getServingSatellite().Cached_Contents_Flag =1;
                            a.getServingSatellite().Cached_Contents.clear();
                            for (int i = 0; i < Cache_Capacity && i < a.Gain_List.size(); i++) {
                                Content temp = null;
                                for (Content c : All_Contents) {
                                    if (c.getContentID() == a.Gain_List.get(i).getKey())
                                        temp = c;
                                }
                                a.getServingSatellite().Cached_Contents.add(temp);
                            }
                            //缓存未满的情况 总共就3次
                            if (a.Gain_List.size() < Cache_Capacity) {
                                System.out.println("Cached contents have not been full ! ");
                                int Files = All_Contents.size();
                                for (int i = 0; i < Cache_Capacity; i++) {
                                    int contentid = r.nextInt(Files);
                                    if (a.getServingSatellite().Cached_Contents.contains(All_Contents.get(contentid))) {
                                        i--;
                                        continue;
                                    }
                                    Content temp = All_Contents.get(contentid);
                                    a.getServingSatellite().Cached_Contents.add(temp);
                                }
                            }
                            //缓存更新完了，地区的这一个更新周期存放的内容就需要清零
                            a.getRequestedContentsOneInterval().clear();
                        }
                    }
                }
            }*/
            else if (CachingPolicyFlag == 5) {
                updateCacheRANDOM();
            } else if (CachingPolicyFlag == 6) {
                updateCacheMostPopular();
            }

        }

        //分布式缓存方式 NonCooperative
        else {
            if(CachingPolicyFlag==4) {
                List<Map.Entry<Content,Double>> local_area_popularity_list=this.Serving_Area.Popularity_List;
                Cached_Contents.clear();

                for (int i = 0; i < Cache_Capacity && i < local_area_popularity_list.size(); i++) {
                    Cached_Contents.add(local_area_popularity_list.get(i).getKey());
                }

                //缓存未满的情况 总共就3次
                if (local_area_popularity_list.size() < Cache_Capacity) {
                    System.out.println("Cached contents have not been full ! ");
                    int Files=All_Contents.size();

                    for(int i=0;i<Cache_Capacity;i++){
                        int contentid=r.nextInt(Files);
                        if(Cached_Contents.contains(All_Contents.get(contentid))){
                            i--;
                            continue;
                        }
                        Content temp= All_Contents.get(contentid);
                        Cached_Contents.add(temp);
                    }
                }
                //缓存更新完了，地区的这一个更新周期存放的内容就需要清零
                this.getServingArea().getRequestedContentsOneInterval().clear();
            }
            else if (CachingPolicyFlag == 5) {
                updateCacheRANDOM();
            } else if (CachingPolicyFlag == 6) {
                updateCacheMostPopular();
            }
        }
    }


    //更新每个卫星，更新其服务的地区及其缓存情况，地区已经更新过了，每个地区的服务卫星ID已经更新
    public void convert(){
         //更新缓存命中率
         RecordCacheHitRatio();
        //更新每个卫星服务的地区
        for(Map.Entry<Integer,Area> entry:All_Areas.entrySet()){
            Area temparea=entry.getValue();
            if(temparea.getServingSatellite().getSatelliteID()==this.Satellite_ID){
                this.Serving_Area=temparea;
                this.Serving_AreaID=temparea.getAreaID();
                this.Serving_Area_Feature=temparea.getArea_Feature();
                break;
            }
        }

    }
    public List<Content> getCached_Contents(){
        return this.Cached_Contents;
    }

    public void setCached_Contents(List<Content> L1){
        this.Cached_Contents.clear();
        this.Cached_Contents.addAll(L1);
    }

    public void setLRUCache(HashMap<Content,Integer> lru1){
        this.LRUCache.clear();
        this.LRUCache=lru1;
    }

    public void setLFUCache(HashMap<Content,Integer> lfu1){
        this.LFUCache.clear();
        this.LFUCache=lfu1;
    }
    public void setFIFOCache(List<Content> fifo1){
        this.FIFOCache.clear();
        this.FIFOCache=fifo1;
    }


    public int getSatelliteID(){
        return this.Satellite_ID;
    }

    public Area getServingArea(){
        return this.Serving_Area;
    }

    public int getServingAreaID(){
        return this.Serving_AreaID;
    }

    public Tuple<Integer,Integer> getServingAreaCoord() {
        Tuple<Integer,Integer> s;
        s = new Tuple<>(Serving_AreaID / Rows, Serving_AreaID % Rows);
        return s;
    }

    public void initServingArea(){
        this.Serving_Area= All_Areas.get(this.Satellite_ID);
        this.Serving_AreaID=this.Serving_Area.getAreaID();
        this.Serving_Area_Feature=this.Serving_Area.getArea_Feature();
    }

    //无任何协作缓存，只从服务该地区的卫星获取内容
    public int isCachedContentDistributed(Content content){
        Total_Received_Request_One_Interval++;
        Total_Received_Request++;
        int contentid=content.getContentID();
        int flag=4;
        //从卫星上取回内容
        if(!this.Cached_Contents.isEmpty()){
            for (int j : Cached_ContentsID)
                if (j == contentid) {
                    Total_Hited_Request_One_Interval++;
                    Total_Hited_Request++;
                    flag = 1;
                    return flag;
                }
        }
        return flag;
    }

    //有协作缓存，只从服务该地区的卫星和其单跳卫星上获取内容
    public int isCachedContentNeighborhood(Content content){
        Total_Received_Request_One_Interval++;
        Total_Received_Request++;
        int contentid=content.getContentID();
        int flag=4;
        //从卫星上取回内容
        if(!this.Cached_Contents.isEmpty()){
            for (int j : Cached_ContentsID)
                if (j == contentid) {
                    Total_Hited_Request_One_Interval++;
                    Total_Hited_Request++;
                    flag = 1;
                    return flag;
                }
        }

        if(!this.Serving_Area.getNeighbor_Areas().isEmpty()){
            List<Area> areaneighbor=this.Serving_Area.getNeighbor_Areas();
            for(Area a:areaneighbor) {
                for(int j:a.getServingSatellite().getCachedContentID()){
                    if(j==contentid){
                        a.getServingSatellite().Total_Hited_Request_From_Neighbor++;
                        flag=2;
                        return flag;
                    }
                }
            }
        }
        return flag;
    }

    //有协作缓存，从划分的协作缓存区域取回内容
    public int isCachedContentCooperative(Content content){
        Total_Received_Request_One_Interval++;
        Total_Received_Request++;
        int contentid=content.getContentID();
        int flag=100;
        //从卫星上取回内容
        if(!this.Cached_Contents.isEmpty()){
            for (int j : Cached_ContentsID)
                if (j == contentid) {
                    Total_Hited_Request_One_Interval++;
                    Total_Hited_Request++;
                    flag = 1;
                    return flag;
                }
        }
        if(!this.Serving_Area.Local_Cooperative_Areas.isEmpty()){
            List<Area> cooperative_areas=this.Serving_Area.Local_Cooperative_Areas;
            for(Area a:cooperative_areas) {
                int distance=getTwoAreaDistance(a,this.Serving_Area);
                 if(distance<=K) {
                    for (int j : a.getServingSatellite().getCachedContentID()) {
                        if (j == contentid) {
                            a.getServingSatellite().Total_Hited_Request_From_Neighbor++;
                            flag = distance+1;
                            return flag;
                        }
                    }
                }
            }
        }
        return flag;
    }

    //卫星缓存内容函数，这个函数应在每次切换后更新，且应在地区更新自己的特征之后
    public void CacheContentsRandom(){
        int Files=All_Contents.size();
        Random r=new Random();
        for(int i=0;i<Cache_Capacity;i++){
            int contentid=r.nextInt(Files);
            if(Cached_Contents.contains(All_Contents.get(contentid))){
                i--;
                continue;
            }
            Content temp= All_Contents.get(contentid);
            Cached_Contents.add(temp);
        }
    }

    //卫星根据LFU策略缓存
    public void CacheContentLFU(){
        Area area=this.getServingArea();
        HashMap<Content,Integer> requestofareamap=area.getRequestMap(area.getRequestedContents());
        List<Map.Entry<Content,Integer>> list=new ArrayList<>(requestofareamap.entrySet());
        Comparator<Map.Entry<Content,Integer>> valueCompartor=new Comparator<Map.Entry<Content, Integer>>() {
            @Override
            public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };
        list.sort(valueCompartor);
        for(int i=0;i<Cache_Capacity&&i<list.size();i++){
            Cached_Contents.add(list.get(i).getKey());
        }
        if(Cached_Contents.size()<Cache_Capacity){
            int Files=All_Contents.size();
            Random r=new Random();
            for(int i=0;i<Cache_Capacity-Cached_Contents.size();i++){
                int contentid=r.nextInt(Files);
                if(Cached_Contents.contains(All_Contents.get(contentid))){
                    i--;
                    continue;
                }
                Content temp= All_Contents.get(contentid);
                Cached_Contents.add(temp);
            }
        }
    }


    public List<Integer> getServingAreaRequestedContentsID(){
        List<Integer> content_id=new ArrayList<>();
        for(Content c: this.Serving_Area.getRequestedContents()){
            content_id.add(c.getContentID());
        }
        return content_id;
    }

    public List<String> getServingAreaRequestedContentsFeature(){
        List<String> content_feature=new ArrayList<>();
        for(Content c: this.Serving_Area.getRequestedContents()){
            content_feature.add(c.getContentFeature());
        }
        return content_feature;
    }

    public Area.Areafeature getServingAreaFeature(){
        return this.Serving_Area.getArea_Feature();
    }

    //记录此卫星的缓存命中率，并用一个链表存储其服务的地区的缓存命中情况
    public void RecordCacheHitRatio(){
        Tuple<Integer,Integer> hitratio=new Tuple<>(Total_Hited_Request_One_Interval, Total_Received_Request_One_Interval);
        if(!this.Cache_Hit_Ratio.containsKey(this.getServingAreaID())){
            Cache_Hit_Ratio.put(this.getServingAreaID(),hitratio);
        }
        else {
            Tuple<Integer,Integer> hitratiobefore=Cache_Hit_Ratio.get(this.getServingAreaID());
            hitratio= new Tuple<>(Total_Hited_Request_One_Interval+hitratiobefore.getKey(),Total_Received_Request_One_Interval+hitratiobefore.getValue());
            Cache_Hit_Ratio.put(this.getServingAreaID(),hitratio);
        }
        Total_Hited_Request_One_Interval =0;
        Total_Received_Request_One_Interval =0;
    }

/*    public double getCacheHitRatio(int serving_AreaID){
        int hittimes=Cache_Hit_Ratio.get(serving_AreaID).getValue().getKey();
        int totaltimes=Cache_Hit_Ratio.get(serving_AreaID).getValue().getValue();
        return (double) hittimes/totaltimes;
    }
    public List<Double> getCacheHitRatio(){
        List<Double> cachehitratiolist=new ArrayList<>();
        for (Tuple<Integer, Tuple<Integer, Integer>> integerTupleTuple : Cache_Hit_Ratio) {
            double hitratio;
            int servingareaid = integerTupleTuple.getKey();
            int hittimes = integerTupleTuple.getValue().getKey();
            int totaltimes = integerTupleTuple.getValue().getValue();
            if (totaltimes == 0)
                hitratio = 0;
            else {
                hitratio = (double) hittimes / totaltimes;
            }
            cachehitratiolist.add(hitratio);
        }
        return cachehitratiolist;
    }*/

    public HashMap<Integer, Tuple<Integer, Integer>> getCacheHitRatio(){
        return Cache_Hit_Ratio;
    }

    public int getTotalReceivedRequest(){
        return this.Total_Received_Request;
    }

    public int getTotalHittedRequestFromLocal(){
        return this.Total_Hited_Request;
    }

    public int getTotalHittedRequestFromNeighbor(){
        return this.Total_Hited_Request_From_Neighbor;
    }
    public int getTotalHittedRequest(){
        return this.Total_Hited_Request+this.Total_Hited_Request_From_Neighbor;
    }
    //LFU LRU FIFO的缓存算法，每来一个请求更新一次
    public void updateCache(Content content,Request request){

        if(CachingPolicyFlag==1){
            updateCacheLFU(content,request);
        }

        else if(CachingPolicyFlag==2){
            updateCacheLRU(content,request);
        }

        else if(CachingPolicyFlag==3){
            updateCacheFIFO(content,request);
        }

        else if(CachingPolicyFlag==4){
            if(this.Serving_Area.Total_Update_Times==0)
                updateCacheLRU(content,request);
        }
        else if(CachingPolicyFlag==6){
            if(this.Serving_Area.Total_Update_Times==0)
                updateCacheLRU(content,request);
        }

        /*   else if(CachingPolicyFlag==5){
                updateCacheRANDOM();
        }*/
    }



    public void updateCacheLFU(Content content,Request request){
        //缓存未满的情况
        if(Cached_Contents.size()<Cache_Capacity){
            Cached_Contents.add(content);
        }
        //缓存满的情况
            //缓存里有该请求
        if(LFULRUCachecontainsContentFromID(content)){
            LFULRUCacheReplaceContent(content,request);
        }

        //缓存里没有该请求
        else {
                //LFU还没有记录满
            if(LFUCache.size()<Cache_Capacity){
                LFUCache.put(content,1);
            }
            else{
                //找一个最不流行的替换
                Comparator<Map.Entry<Content,Integer>> valueCompartor=new Comparator<Map.Entry<Content, Integer>>() {
                    @Override
                    public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                        return o2.getValue()- o1.getValue();
                    }
                };
                List<Map.Entry<Content,Integer>> LFUCacheList=new ArrayList<>(LFUCache.entrySet());
                try {
                    LFUCacheList.sort(valueCompartor);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                LFUCache.remove(LFUCacheList.get(Cache_Capacity-1).getKey());
                LFUCache.put(content,1);
            }
        }
            Cached_Contents.clear();
            Cached_Contents.addAll(LFUCache.keySet());
    }


    public void updateCacheLRU(Content content,Request request){
        if(Cached_Contents.size()<Cache_Capacity)
            Cached_Contents.add(content);
        if(LFULRUCachecontainsContentFromID(content)){
                LFULRUCacheReplaceContent(content,request);
        }

        else {
            if(LRUCache.size()<Cache_Capacity){
                LRUCache.put(content,request.getRequest_Time());
            }
            else {
                Comparator<Map.Entry<Content,Integer>> valueCompartor=new Comparator<Map.Entry<Content, Integer>>() {
                    @Override
                    public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                        return o2.getValue()- o1.getValue();
                    }
                };
                List<Map.Entry<Content,Integer>> LRUCacheList=new ArrayList<>(LRUCache.entrySet());
                LRUCacheList.sort(valueCompartor);
                LRUCache.remove(LRUCacheList.get(Cache_Capacity-1).getKey());
                LRUCache.put(content,request.getRequest_Time());
            }
        }
        Cached_Contents.clear();
        Cached_Contents.addAll(LRUCache.keySet());
    }

    //随机缓存策略
    public void updateCacheRANDOM(){
        int Files=All_Contents.size();
        Cached_Contents.clear();
        for(int i=0;i<Cache_Capacity;i++){
            int contentid=random.nextInt(Files);
            if(Cached_Contents.contains(All_Contents.get(contentid))){
                i--;
                continue;
            }
            Content temp= All_Contents.get(contentid);
            Cached_Contents.add(temp);
        }
    }

    public void updateCacheFIFO(Content content,Request request){
        if(FIFOCache.size()<Cache_Capacity){
            FIFOCache.add(content);
        }
        else {
            FIFOCache.remove(0);
            FIFOCache.add(content);
        }
        Cached_Contents.clear();
        Cached_Contents.addAll(FIFOCache);
    }


    public void updateCacheMostPopular(){
/*        List<Map.Entry<Content,Double>> poplist=this.Serving_Area.Popularity_List;
        Comparator<Map.Entry<Content,Double>> valuecompartor=new Comparator<Map.Entry<Content, Double>>() {
            @Override
            public int compare(Map.Entry<Content, Double> o1, Map.Entry<Content, Double> o2) {
                int flag=0;
                if(o2.getValue()-o1.getValue()>0) flag=1;
                else if(o2.getValue()-o1.getValue()<0)flag=-1;
                return flag;
            }
        };
        poplist.sort(valuecompartor);
        Cached_Contents.clear();
        for(int i=0;i<poplist.size()&&i<Cache_Capacity;i++){
            Cached_Contents.add(poplist.get(i).getKey());
        }*/
/*
        int size=this.Serving_Area.Request_Records_Sorted.size();
        List<Map.Entry<Content,Integer>> poplist=this.Serving_Area.Request_Records_Sorted.get(size-1);
        Comparator<Map.Entry<Content,Integer>> valuecompartor=new Comparator<Map.Entry<Content, Integer>>() {
            @Override
            public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                int flag=0;
                if(o2.getValue()-o1.getValue()>0) flag=1;
                else if(o2.getValue()-o1.getValue()<0)flag=-1;
                return flag;
            }
        };
        poplist.sort(valuecompartor);
        Cached_Contents.clear();
        for(int i=0;i<poplist.size()&&i<Cache_Capacity;i++){
            Cached_Contents.add(poplist.get(i).getKey());
        }*/
        Comparator<Map.Entry<Integer,Integer>> valueCompartor=new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };
        //List<Area> coopeativeareas=this.Serving_Area.Local_Cooperative_Areas;

        HashMap<Integer,Integer> contentrequestimes=new HashMap<>();
        for(Area a:All_Areas.values()){
            for(Content c:a.Requested_Contents){
                if(!contentrequestimes.containsKey(c.getContentID())){
                    contentrequestimes.put(c.getContentID(),1);
                }
                int oldvalue=contentrequestimes.get(c.getContentID());
                int newvalue=oldvalue+1;
                contentrequestimes.replace(c.getContentID(),oldvalue,newvalue);
            }
        }
        List<Map.Entry<Integer,Integer>> contentrequestimeslist=new ArrayList<>(contentrequestimes.entrySet());
        contentrequestimeslist.sort(valueCompartor);
        Cached_Contents.clear();
        for(int i=0;i<contentrequestimeslist.size()&&i<Cache_Capacity;i++){
            int contentid=contentrequestimeslist.get(i).getKey();
            Content c1 = null;
            for(int j=0;j<All_Contents.size();j++) {
                if (All_Contents.get(j).getContentID() == contentid) {
                    c1 = All_Contents.get(j);
                }
            }
            Cached_Contents.add(c1);
        }
    }

    public void updateCacheRidge(Content content, Request request){

        Comparator<Map.Entry<Content,Integer>> valueCompartor=new Comparator<Map.Entry<Content, Integer>>() {
            @Override
            public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                return o2.getValue()- o1.getValue();
            }
        };
            if(Cached_Contents.size()<Cache_Capacity){
                Cached_Contents.add(content);
            }
            if(LFULRUCachecontainsContentFromID(content)){
                LFULRUCacheReplaceContent(content,request);
            }
            //缓存里没有该请求
            else {

                //LFU还没有记录满
                if(RidgeCache.size()<Cache_Capacity){
                    RidgeCache.put(content,1);
                }
                else{
                    //找一个最不流行的替换

                    List<Map.Entry<Content,Integer>> LFUCacheList=new ArrayList<>(RidgeCache.entrySet());
                    try {
                        LFUCacheList.sort(valueCompartor);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    RidgeCache.remove(LFUCacheList.get(Cache_Capacity-1).getKey());
                    RidgeCache.put(content,1);
                }
            }

        Cached_Contents.clear();
        Cached_Contents.addAll(RidgeCache.keySet());
    }

    public double getTwoArrayMultiply(double[] a1,double[] a2){
        double res=0;
        for(int i=0;i<a1.length;i++){
            res+=a1[i]*a2[i];
        }
        return res;
    }

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

    public int[] getCachedContentID(){
        int[] res=new int[Cache_Capacity];
        for(int i=0;i<Cache_Capacity&&i<Cached_Contents.size();i++){
            res[i]=Cached_Contents.get(i).getContentID();
        }
        for(int i=0;i<res.length;i++){
            for(int j=i+1;j<res.length;j++){
                if(res[i]>res[j]){
                    int temp=res[i];
                    res[i]=res[j];
                    res[j]=temp;
                }
            }
        }
        return res;
    }

    public Area getAreaFromAreaID(int id){
        for(Area a:All_Areas.values()){
            if(a.getAreaID()==id)
                return a;
        }
        return null;
    }

    public HashMap<Content,Integer> getLRUCache(){
        return this.LRUCache;
    }

    public HashMap<Content,Integer> getLFUCache(){
        return this.LFUCache;
    }

    public List<Content> getFIFOCache(){
        return this.FIFOCache;
    }
    public HashMap<Content,Integer> getRidgeCache(){
        return this.RidgeCache;
    }

    public void setRidgeCache(HashMap<Content,Integer> ridgeCache){
        this.RidgeCache.clear();
        this.RidgeCache=ridgeCache;
    }


    public boolean LFULRUCachecontainsContentFromID(Content content){

        if(CachingPolicyFlag==1){
            for(Content c:LFUCache.keySet()){
                if(c.getContentID()==content.getContentID())
                    return true;
            }
            return false;
        }
        else if(CachingPolicyFlag==2){
            for(Content c:LRUCache.keySet()){
                if(c.getContentID()==content.getContentID())
                    return true;
            }
            return false;
        }
        return false;

    }

    public void LFULRUCacheReplaceContent(Content content, Request request){
        if(CachingPolicyFlag==1){
            for(Map.Entry<Content,Integer> ent:LFUCache.entrySet()){
                if(ent.getKey().getContentID()==content.getContentID()){
                    ent.setValue(ent.getValue()+1);
                }
            }
        }
        if(CachingPolicyFlag==2){
            for(Map.Entry<Content,Integer> ent:LRUCache.entrySet()){
                if(ent.getKey().getContentID()==content.getContentID()){
                    ent.setValue(request.getRequest_Time());
                }
            }
        }
    }

    public int maxposThreeNum(double a,double b,double c){
        if(a>b&&a>c)return 0;
        if(b>a&&b>c)return 1;
        return 2;
    }


    public double getListEntryvalue(int contentid,List<Map.Entry<Content,Double>> popularitylist){
        double res=0;
        for(int i=0;i<popularitylist.size();i++){
            if(popularitylist.get(i).getKey().getContentID()==contentid)
                res=popularitylist.get(i).getValue();
        }
        return res;
    }

    public int getPointerofTuplesList(int contentid, List<Tuple<Integer,Double>> gainlist){
        int res=2000;
        for(int i=0;i<gainlist.size();i++){
            if(gainlist.get(i).getKey().equals(contentid)){
                res=i;
                break;
            }
        }
        return res;
    }

    @Override
    public int getTwoAreaDistance(Area a, Area b) {
        return Math.abs(a.getAreaCoord().getKey()-b.getAreaCoord().getKey())+Math.abs(a.getAreaCoord().getValue()-b.getAreaCoord().getValue());
    }

    public int[] getToCachedContentID(List<Tuple<Integer,Double>> gainlist){
        int[] res=new int[Cache_Capacity];
        for(int i=0;i<gainlist.size()&&i<Cache_Capacity;i++){
            int contentid=gainlist.get(i).getKey();
            res[i]=contentid;
        }
        selectSort(res);
        return res;
    }
    public void selectSort(int[] res ) {
        for (int i = 0; i < res.length; i++) {
            for (int j = i + 1; j < res.length; j++) {
                if (res[i] > res[j]) {
                    int temp = res[i];
                    res[i] = res[j];
                    res[j] = temp;
                }
            }
        }
    }

    public boolean issamearray(int[] a1,int[] a2){
        for(int i=0;i<Cache_Capacity;i++){
            if(a1[i]!=a2[i])
                return false;
        }
        return true;
    }


}
