package Terrestrial;

import ContentFiles.Content;
import Satellites.*;

import util.Tuple;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static ContentFiles.Content.Content_Feature_Dimension;
import static Satellites.Satellite.*;
import static Terrestrial.TerrestrialUsers.All_Users;


public class Area implements CalAreaDistance{

    private Satellite Serving_Satellite;
    private final int AreaID;
    private final Tuple<Integer,Integer> AreaCoord;
    public static HashMap<Integer,Area> All_Areas =new HashMap<Integer,Area>();
    private final List<Area> Neighbor_Areas=new ArrayList<>();
    public static HashMap<Area,Integer> Static_Cooperative_Areas =new HashMap<>();
    public  List<Area> Local_Cooperative_Areas=new ArrayList<>();
    public  List<Area> Cooperative_Areas=new ArrayList<>();
    public List<Map.Entry<Content, Double>> Popularity_List = new ArrayList<>();
    public List<Tuple<Integer,Double>> Gain_List=new ArrayList<>();
    public static List<Map.Entry<Area,Integer>> Cooperative_Areas_List=new ArrayList<>();
    public int CooperativeFlag=0;
    public int CooperativeAreasID;
    public int CooperativeCacheFlag=0;
    public final List<Content> Requested_Contents=new ArrayList<>();
    private final List<Content> Requested_Contents_One_Interval=new ArrayList<>();
    public final HashMap<Integer,Integer> Requested_Contents_Times=new HashMap<>();
    public List<Map.Entry<Integer,Integer>> Requested_Contents_Times_List=new ArrayList<>();
    public int Request_Times=0;
    public int Hitted_Request_Times=0;
    public int Hitted_Request_Times_Neighbor=0;
    public final HashMap<Integer,HashMap<Content,Integer>> Request_Records_EachUpdate =new HashMap<>();
    public final HashMap<Integer,List<Map.Entry<Content,Integer>>> Request_Records_EachUpdate_Sorted =new HashMap<>();
    public final HashMap<Integer,HashMap<Content,Integer>> Request_Records =new HashMap<>();
    public final HashMap<Integer,List<Map.Entry<Content,Integer>>> Request_Records_Sorted =new HashMap<>();
    private final List<TerrestrialUsers> Area_Users=new ArrayList<>();
    private Areafeature Area_Feature;

    private static final int Ridge_Prediction_Dimension=10;
    public int Total_Update_Times=0;



    /**请求矩阵C
     */
    private final int[][] Request_Matrix=new int[Ridge_Prediction_Dimension][Content_Feature_Dimension];


    @Override
    public int getTwoAreaDistance(Area a, Area b) {
        return Math.abs(a.getAreaCoord().getKey()-b.getAreaCoord().getKey())+Math.abs(a.getAreaCoord().getValue()-b.getAreaCoord().getValue());

    }

    public class Areafeature {
        private String Current_Feature ="";
        private final double[] Current_Feature_Array;
        Areafeature(){
            Current_Feature_Array= caculateCurrentFeatureByRidgetxt();
            Current_Feature = Arrays.toString(Current_Feature_Array);
            }
       public double[] getCurrentFeatureArray(){
            return this.Current_Feature_Array;
       }
    }

    public Area (int areaID,Satellite inintalservingsate){
        this.AreaID=areaID;
        this.Serving_Satellite=inintalservingsate;
        this.AreaCoord=new Tuple<>(AreaID/ Rows,AreaID% Rows);

    }

    //地区初始化函数，获取地区用户以及计算邻居区域
    public void init(){
         setAreaUsers();
         SetNeighborAreas();
         initAreaFeature();

    }

    public int getAreaID(){
        return this.AreaID;
    }

    public Satellite getServingSatellite(){
        return this.Serving_Satellite;
    }

    public Tuple<Integer,Integer> getAreaCoord(){
        return this.AreaCoord;
    }

    public int AreaCoordtoAreaID(Tuple<Integer,Integer> AreaCoord){
        return AreaCoord.getKey()* Rows +AreaCoord.getValue();

    }

    public List<Tuple<Integer,Integer>> getNeighborAreasCoord(){
        List<Tuple<Integer,Integer>> Neighbor_AreasCoord=new ArrayList<>();
        for(Area temp:Neighbor_Areas){
            if(temp!=null)
                Neighbor_AreasCoord.add(temp.AreaCoord);
        }
        return Neighbor_AreasCoord;
    }

    //计算邻居区域，通过坐标的形式来计算邻居
    public void SetNeighborAreas(){
        List<Tuple<Integer,Integer>> NeighborAreaCoord=new ArrayList<>();
        Tuple<Integer,Integer> temp_up=new Tuple<>(AreaCoord.getKey(),AreaCoord.getValue()+1);
        Tuple<Integer,Integer> temp_below=new Tuple<>(AreaCoord.getKey(),AreaCoord.getValue()-1);
        Tuple<Integer,Integer> temp_left=new Tuple<>(AreaCoord.getKey()-1,AreaCoord.getValue());
        Tuple<Integer,Integer> temp_right=new Tuple<>(AreaCoord.getKey()+1,AreaCoord.getValue());
        if(temp_up.getValue()< Rows)
           NeighborAreaCoord.add(temp_up);
        if(temp_below.getValue()>=0)
           NeighborAreaCoord.add(temp_below);
        if(temp_left.getKey()>=0)
           NeighborAreaCoord.add(temp_left);
        if(temp_right.getKey()< Columns)
           NeighborAreaCoord.add(temp_right);
        for(Tuple<Integer,Integer> temp:NeighborAreaCoord){
            int tempAreaID=AreaCoordtoAreaID(temp);
            if(!Neighbor_Areas.contains(All_Areas.get(tempAreaID)))
                Neighbor_Areas.add(All_Areas.get(tempAreaID));
        }
    }

    public double getAreasSimilarity(Area a1,Area a2){
        double sim;
        double[] a1feature=a1.getAreaFeatureArray();
        double[] a2feature=a2.getAreaFeatureArray();
        int[] a1_preference=preferenceSort(a1feature);
        int[] a2_preference=preferenceSort(a2feature);
        int[] preference_difference=getTwoArraySubtract(a1_preference,a2_preference);
        double preference_differenceNorm2=getNorm2(preference_difference);
        double a1_preferenceNorm2=getNorm2(a1_preference);
        double a2_preferenceNorm2=getNorm2(a2_preference);
        sim=1-Math.pow(preference_differenceNorm2,2)/(a1_preferenceNorm2*a2_preferenceNorm2);
        if(Math.abs(sim)>1){
            sim=0.99;
        }
        return sim;
    }
    public List<Area> getNeighbor_Areas(){
        return this.Neighbor_Areas;
    }

    public void updateRequestedContent(Content content){
        if(Request_Records_EachUpdate.get(Total_Update_Times)==null){
            HashMap<Content,Integer> requestrecordthisinterval=new HashMap<>();
            requestrecordthisinterval.put(content,1);
            Request_Records_EachUpdate.put(Total_Update_Times,requestrecordthisinterval);
        }
        else {
            if(Request_Records_EachUpdate.get(Total_Update_Times).containsKey(content)){
                int times= Request_Records_EachUpdate.get(Total_Update_Times).get(content)+1;
                Request_Records_EachUpdate.get(Total_Update_Times).replace(content,times);
            }
            else {
                Request_Records_EachUpdate.get(Total_Update_Times).put(content,1);
            }
        }
        Requested_Contents_One_Interval.add(content);
        Requested_Contents.add(content);
    }

    public List<Content> getRequestedContentsOneInterval(){
        return this.Requested_Contents_One_Interval;
    }

    public List<Content> getRequestedContents(){
        return this.Requested_Contents;
    }

    public int[] getRequestedContentsOneIntervalID(){
        int[] res=new int[Requested_Contents_One_Interval.size()];
        for(int i=0;i<Requested_Contents_One_Interval.size();i++){
            res[i]=Requested_Contents_One_Interval.get(i).getContentID();
        }
        return res;
    }

    public List<Integer> getRequestedContentsID(){
        List<Integer> content_id=new ArrayList<>();
        for(Content c:Requested_Contents){
            content_id.add(c.getContentID());
        }
        return content_id;
    }

    public List<String> getRequestedContentsFeature(List<Content> Contents){
        List<String> content_feature=new ArrayList<>();
        for(Content c:Contents){
            content_feature.add(c.getContentFeature());
        }
        return content_feature;
    }

    //初始化地区特征
    public void initAreaFeature(){
        this.Area_Feature= new Areafeature();
    }

    //获取地区特征
    public Areafeature getArea_Feature(){
        return this.Area_Feature;
    }

    public String getAreaFeatureString(){
        return this.Area_Feature.Current_Feature;
    }

    public double[] getAreaFeatureArray(){
        return this.Area_Feature.Current_Feature_Array;
    }

    //更新每个地区的服务卫星
    public void convert(){
        int serving_satellite_id=this.getServingSatellite().getSatelliteID();
        int next_serving_satellite_id=(serving_satellite_id+1)% Rows +(serving_satellite_id/ Rows)* Rows;
        this.Serving_Satellite=getSatelliteFromSatelliteID(next_serving_satellite_id);
    }

    public void update(){
        this.Total_Update_Times++;
        this.Area_Feature= new Areafeature();


        Comparator<Map.Entry<Content,Integer>> valueComparatorInteger=new Comparator<Map.Entry<Content, Integer>>() {
            @Override
            public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };
        Comparator<Map.Entry<Content, Double>> valueComparatorDouble = new Comparator<Map.Entry<Content, Double>>() {
            @Override
            public int compare(Map.Entry<Content, Double> o1, Map.Entry<Content, Double> o2) {

                double flag = o2.getValue() - o1.getValue();
                if (flag > 0) return 1;
                else if (flag < 0) return -1;
                else return 0;
            }
        };
        Comparator<Map.Entry<Integer,Integer>> valueComparatorIntegerInteger=new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };

        HashMap<Content, Integer> temp1 = new HashMap<>();
        for(Content c:this.Requested_Contents){
            if(!temp1.containsKey(c)){
                temp1.put(c,1);
            }
            else temp1.replace(c, temp1.get(c)+1);
        }
        Request_Records.put(Total_Update_Times-1,temp1);
        List<Map.Entry<Content,Integer>> templist1=new ArrayList<>(Request_Records.get(Total_Update_Times-1).entrySet());
        templist1.sort(valueComparatorInteger);
        Request_Records_Sorted.put(Total_Update_Times-1,templist1);


        //记录每次更新时 上一个周期内Total_Update_Times-1的内容请求数 如果上一个周期Total_Update_Times-1内没有请求
        //那么 Request_Records是没有任何记录的，要创建一个空map 赋值给 Request_Records
        if (Request_Records_EachUpdate.get(Total_Update_Times-1)==null) {
            HashMap<Content, Integer> temp = new HashMap<>();
            Request_Records_EachUpdate.put(Total_Update_Times - 1, temp);
        }
        List<Map.Entry<Content,Integer>> templist=new ArrayList<>(Request_Records_EachUpdate.get(Total_Update_Times-1).entrySet());
        templist.sort(valueComparatorInteger);
        Request_Records_EachUpdate_Sorted.put(Total_Update_Times-1,templist);

        //更新接下来这一周期内的流行度表，流行度是基于地区特征预测后的预测流行度
        Popularity_List.clear();
        HashMap<Content, Double> contentpopularity = new HashMap<>();
        double[] Area_Feature_Array = this.getArea_Feature().getCurrentFeatureArray();
        /*for (int i=0;i<this.getRequestedContentsOneInterval().size()&&i<50;i++) {
            Content c=this.getRequestedContentsOneInterval().get(i);
            double popularityofc = getTwoArrayMultiply(Area_Feature_Array, c.getContentFeatureArray());
            contentpopularity.put(c, popularityofc);
        }*/
        for (int i = 0; i<this.Request_Records_EachUpdate_Sorted.get(Total_Update_Times-1).size(); i++) {
            Content c=this.Request_Records_EachUpdate_Sorted.get(Total_Update_Times-1).get(i).getKey();
            double popularityofc = getTwoArrayMultiply(Area_Feature_Array, c.getContentFeatureArray());
            contentpopularity.put(c, popularityofc);
        }
        Popularity_List = new ArrayList<>(contentpopularity.entrySet());
        Popularity_List.sort(valueComparatorDouble);

        //Requested_Contents_One_Interval在这里不急着清零，等卫星依据这个缓存完之后，再清零，见函数Satellite.update()
        //this.Requested_Contents_One_Interval.clear();


    }

    public void setAreaUsers(){
         for(TerrestrialUsers User:All_Users.values()){
             if(User.getAreaID()==this.AreaID){
                 Area_Users.add(User);
             }
         }
    }

    //得到该地区用户获取内容的平均延迟
    public double getAreaRetrieveDelay(){
         double totaldelay=0;
         int totalrequest=0;
         for(TerrestrialUsers user:this.Area_Users){
             totaldelay+=user.getUserRetrieveTotalDelay();
             totalrequest+=user.getUserRequestedContent().size();
         }

        if(this.Requested_Contents.size()==0) {
            return 0;
        }

        return totaldelay/this.Requested_Contents.size();
    }

    public int[] getAreaUserID(){
         int[] areauserid=new int[Area_Users.size()];
         for (int i=0;i<Area_Users.size();i++){
             areauserid[i]=Area_Users.get(i).getUserId();
         }
         return areauserid;
    }

    public Satellite getSatelliteFromSatelliteID(int id){
        for(Satellite s:All_Satellites){
            if(s.getSatelliteID()==id)
                return s;
        }
        return null;
    }

    public HashMap<Content,Integer> getRequestMap(List<Content> contentList){
        HashMap<Content,Integer> requestsmap=new HashMap<>();
        for(Content c:contentList){
            int t=1;
            if(!requestsmap.containsKey(c)){
                requestsmap.put(c,t);
            }
            else {
                int c_times=requestsmap.get(c);
                requestsmap.replace(c,c_times,++c_times);
            }
        }
        return requestsmap;
    }

    public  double[] caculateCurrentFeatureByRidgetxt()  {
        //init阶段
        if(this.Total_Update_Times==0){
            return new double[Content_Feature_Dimension];
        }

        else {
            double[] feature = new double[Content_Feature_Dimension];
            StringBuilder featurestr = new StringBuilder();
            StringBuilder featurestr1 = new StringBuilder();

            int LastPeriod=this.Total_Update_Times-1;

            //根据岭回归算法确定的最优lamda值 计算每个周期内的前100个流行内容
            String folder=".\\Featurefrompy\\FeatureFrompyAreaNum"+Area_Numbers+"\\FeatureT"+Time_Interval_update+"\\";
            String file="Area"+this.getAreaID()+"_UpdatePeriod"+LastPeriod+".txt";
            String path=folder+file;


            try{
                FileReader f = new FileReader(path);
                BufferedReader bf = new BufferedReader(f);
                String line = bf.readLine();
                while (line != null) {
                    featurestr.append(line);
                    line = bf.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            featurestr = new StringBuilder(featurestr.substring(featurestr.toString().indexOf('[') + 1, featurestr.toString().indexOf(']')));
            while ( featurestr.charAt(0)==' '){
                featurestr = new StringBuilder(featurestr.substring(1));
            }

            for (int i = 0; i < featurestr.length() - 1; i++) {

                if (featurestr.charAt(i) == ' ' && featurestr.charAt(i + 1) == ' ') {
                    continue;
                }
                featurestr1.append(featurestr.charAt(i));
            }
            List<String> stringlist = new ArrayList<>();
            for (int i = 0; i < Content_Feature_Dimension; i++) {
                String tempstring ="";
                if(i==Content_Feature_Dimension-1){
                    tempstring= featurestr1.toString();
                }
                else {
                    tempstring=featurestr1.substring(0, featurestr1.toString().indexOf(' '));

                }
                stringlist.add(tempstring);
                featurestr1 = new StringBuilder(featurestr1.substring(featurestr1.toString().indexOf(' ') + 1, featurestr1.length()));
            }
            for (int i = 0; i < stringlist.size(); i++) {

                    double stodouble = Double.parseDouble(stringlist.get(i));
                    feature[i] = stodouble;

            }
            return feature;
        }
    }


    public int[][] getRequestMatrix(){
        return Request_Matrix;
    }

    public int[][] getTransposeArray(int[][] arr){
        int arr_x=getArrayDimension(arr).getKey();
        int arr_y=getArrayDimension(arr).getValue();
        int[][] arrtran=new int[arr_y][arr_x];
        for(int i=0;i<arr_x;i++){
            for(int j=0;j<arr_y;j++){
                arrtran[j][i]=arr[i][j];
            }
        }
        return arrtran;
    }
    public double[][] getTransposeArray(double[][] arr){
        int arr_x=getArrayDimension(arr).getKey();
        int arr_y=getArrayDimension(arr).getValue();
        double[][] arrtran=new double[arr_y][arr_x];
        for(int i=0;i<arr_x;i++){
            for(int j=0;j<arr_y;j++){
                arrtran[j][i]=arr[i][j];
            }
        }
        return arrtran;
    }

    public Tuple<Integer,Integer> getArrayDimension(int[][] arr){
        int dim_x=0;
        int dim_y=0;
        dim_y=arr[0].length;
        dim_x=arr.length;
        return new Tuple<>(dim_x,dim_y);
    }

    public Tuple<Integer,Integer> getArrayDimension(double[][] arr){
        int dim_x=0;
        int dim_y=0;
        dim_y=arr[0].length;
        dim_x=arr.length;
        return new Tuple<>(dim_x,dim_y);
    }


    public int[][] getTwoArrayMultiply(int[][] arr1,int[][] arr2){
        int arr1_x=getArrayDimension(arr1).getKey();
        int arr1_y=getArrayDimension(arr1).getValue();
        int arr2_x=getArrayDimension(arr2).getKey();
        if(arr1_y!=arr2_x){
            System.out.println("Error: Array cannot multiply!");
        }
        int arr2_y=getArrayDimension(arr2).getValue();
        int [][] arr3=new int[arr1_x][arr2_y];
        for(int i=0;i<arr1_x;i++){
            for(int j=0;j<arr2_y;j++){
                arr3[i][j]=0;
              for(int k=0;k<arr1_y;k++){
                  arr3[i][j]+=arr1[i][k]*arr2[k][j];
              }
            }
        }
        return arr3;
    }

    public double[][] getTwoArrayMultiply(double[][] arr1,double[][] arr2){
        int arr1_x=getArrayDimension(arr1).getKey();
        int arr1_y=getArrayDimension(arr1).getValue();
        int arr2_x=getArrayDimension(arr2).getKey();

        if(arr1_y!=arr2_x){
            Exception e = new Exception();
            e.printStackTrace();
        }

        int arr2_y=getArrayDimension(arr2).getValue();
        double [][] arr3=new double[arr1_x][arr2_y];
        for(int i=0;i<arr1_x;i++){
            for(int j=0;j<arr2_y;j++){
                arr3[i][j]=0;
                for(int k=0;k<arr1_y;k++){
                    double m = (double) arr1[i][k];
                    double n = (double) arr2[k][j];
                    arr3[i][j]+=m*n;
                }
            }
        }
        return arr3;
    }


    //求单位矩阵
    private double[][] getIndentityArray(int a){
        double[][] identitymatrix =new double[a][a];
        for(int i=0;i<a;i++){
            for (int j=0;j<a;j++){
                if(i==j){
                    identitymatrix[i][j]=1;
                }
                else {
                    identitymatrix[i][j]=0;
                }
            }
        }
        return identitymatrix;
    }

    public double[][] getIntToDouble(int[][] arr){
        int arr_x=arr.length;
        int arr_y=arr[0].length;
        double[][] res=new double[arr_x][arr_y];
        for(int i=0;i<arr_x;i++){
            for (int j=0;j<arr_y;j++){
                res[i][j]=arr[i][j];
            }
        }
        return res;
    }


    private double[][] getTwoArrarPlus(int[][] a,double u){
        int a_x=getArrayDimension(a).getKey();
        int a_y=getArrayDimension(a).getValue();
        double[][] res=new double[a_x][a_y];
        if(a_x==a_y){
            double[][] identityarray=getIndentityArray(a_x);
            for(int i=0;i<a_x;i++){
                for (int j=0;j<a_y;j++){
                    if(i==j){
                        res[i][j]=a[i][j]+u*identityarray[i][j];
                    }
                    else
                        res[i][j]=a[i][j];
                }
            }
        }
        return res;
    }

    private double[][] getTwoArrarPlus(double[][] a,double u){
        int a_x=getArrayDimension(a).getKey();
        int a_y=getArrayDimension(a).getValue();
        double[][] res=new double[a_x][a_y];
        if(a_x==a_y){
            double[][] identityarray=getIndentityArray(a_x);
            for(int i=0;i<a_x;i++){
                for (int j=0;j<a_y;j++){
                    if(i==j){
                        res[i][j]=a[i][j]+u*identityarray[i][j];
                    }
                    else
                        res[i][j]=a[i][j];
                }
            }
        }
        return res;
    }

    private double[][] getTwoMatrixPlus(int[][] a,int[][] b){
        int a_x=getArrayDimension(a).getKey();
        int a_y=getArrayDimension(a).getValue();
        double[][] res=new double[a_x][a_y];
        if(a_x==a_y){
            double[][] identityarray=getIndentityArray(a_x);
            for(int i=0;i<a_x;i++){
                for (int j=0;j<a_y;j++){
                    if(i==j){
                        res[i][j]=a[i][j]+identityarray[i][j];
                    }
                    else
                        res[i][j]=a[i][j];
                }
            }
        }

        else {
            for(int i=0;i<a_x;i++){
                for (int j=0;j<a_y;j++){
                    if(i==j){
                        res[i][j]=a[i][j]+b[i][j];
                    }
                    else
                        res[i][j]=a[i][j];
                }
            }
        }

        return res;
    }

    public double[] getTwoArraySubtract(double[] g1,double[] g2){
        double[] g1_g2=new double[g1.length];
        for(int i=0;i<g1.length;i++){
            g1_g2[i]=g1[i]-g2[i];
        }
        return g1_g2;
    }
    public int[] getTwoArraySubtract(int[] g1,int[] g2){
        int[] g1_g2=new int[g1.length];
        for(int i=0;i<g1.length;i++){
            g1_g2[i]=g1[i]-g2[i];
        }
        return g1_g2;
    }

    public double getNorm2(double[] g){
        double res=0;
        for (double v : g) {
            res += Math.pow(v, 2);
        }
        res=Math.pow(res,0.5);
        return res;
    }

    public double getNorm2(int[] g){
        double res=0;
        for (double v : g) {
            res += Math.pow(v, 2);
        }
        res=Math.pow(res,0.5);
        return res;
    }

    public List<Map.Entry<Content,Integer>> sortDesendMapValueCompartor(HashMap<Content,Integer> requestrecord){
        HashMap<Content,Integer> sortedrequestrecoed=new HashMap<>();
        Comparator<Map.Entry<Content,Integer>> valueCompartor=new Comparator<Map.Entry<Content, Integer>>() {
            @Override
            public int compare(Map.Entry<Content, Integer> o1, Map.Entry<Content, Integer> o2) {
                return o2.getValue()-o1.getValue();
            }
        };
        List<Map.Entry<Content,Integer>> requestrecordlist=new ArrayList<>(requestrecord.entrySet());
        requestrecordlist.sort(valueCompartor);
        return requestrecordlist;
    }



    public void setCooperative_Areas(){
        if(this.CooperativeFlag==0){
            List<Area> possibleCooperativeAreas = new ArrayList<>(Neighbor_Areas);
            List<Area> confirmCooprativeAreas=new ArrayList<>();
            confirmCooprativeAreas.add(this);
            this.CooperativeFlag=1;
            this.CooperativeAreasID=this.getAreaID();
            if(Static_Cooperative_Areas.containsKey(this))
                Static_Cooperative_Areas.replace(this,this.CooperativeAreasID);
            else
                Static_Cooperative_Areas.put(this,this.CooperativeAreasID);


            int areanumbers=1;

            for(int i=0;i<possibleCooperativeAreas.size();i++){
                Area a=possibleCooperativeAreas.get(i);
                if(a.CooperativeFlag==0) {
                    int flag = 1;
                    for (int j = 0; j < confirmCooprativeAreas.size(); j++) {
                        Area ca = confirmCooprativeAreas.get(j);
                        double sim = getAreasSimilarity(ca, a);

                        //如果与当前区域的相似性满足条件
                        if (Math.abs(sim) < Similarity) {
                            flag = 0;
                            break;
                        }
                    }
                    if (flag == 1&&areanumbers<Cooperative_Areas_Size) {

                        confirmCooprativeAreas.add(a);
                        a.CooperativeFlag = 1;
                        a.CooperativeAreasID = this.CooperativeAreasID;
                        //possibleCooperativeAreas.remove(a);
                        Static_Cooperative_Areas.put(a, a.CooperativeAreasID);
                        areanumbers++;
                        for (Area ana : a.Neighbor_Areas) {
                            if (!confirmCooprativeAreas.contains(ana) && !possibleCooperativeAreas.contains(ana) && ana.CooperativeFlag == 0) {
                                possibleCooperativeAreas.add(ana);
                            }
                        }
                    }
                }

            }
        }
    }

    public int[] getLocalCooperativeAreasID(){
        int[] res=new int[this.Local_Cooperative_Areas.size()];
        for(int i=0;i<res.length;i++){
            res[i]=this.Local_Cooperative_Areas.get(i).getAreaID();
        }
        selectSort(res);
        return res;
    }

    public int[] getCooperativeAreasID(){
        int[] res=new int[this.Cooperative_Areas.size()];
        for(int i=0;i<res.length;i++){
            res[i]=this.Cooperative_Areas.get(i).getAreaID();
        }
        selectSort(res);
        return res;
    }

    public int[] getNeighborAreasID(){
        int[] res=new int[this.Neighbor_Areas.size()];
        for(int i=0;i<res.length;i++){
            res[i]=this.Neighbor_Areas.get(i).getAreaID();
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
    public int[] preferenceSort(double[] res ) {
        double [] ans=new double[res.length];
        int [] ans1=new int[res.length];
        for (int i = 0; i < ans.length; i++) {
            for (int j = i + 1; j < ans.length; j++) {
                if (ans[i] > ans[j]) {
                    double temp = ans[i];
                    ans[i] = ans[j];
                    ans[j] = temp;
                }
            }
        }
        double midvalue=ans[res.length/2];
        for(int i=0;i<res.length;i++){
            if(res[i]<midvalue)
                ans1[i]=0;
            else
                ans1[i]=1;
        }
        return ans1;
    }

    public double getTwoArrayMultiply(double[] a1,double[] a2){
        double res=0;
        for(int i=0;i<a1.length;i++){
            res+=a1[i]*a2[i];
        }
        return res;
    }

}
