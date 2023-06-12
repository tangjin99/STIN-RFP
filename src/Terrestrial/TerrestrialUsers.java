package Terrestrial;
import ContentFiles.Content;
import Satellites.*;
import util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Satellites.Satellite.*;
import static Terrestrial.Area.All_Areas;
public class TerrestrialUsers {
    private final int UserId;
    private final int Zipcode;
    //默认是从地面站取回内容 标志为为3 为1则从卫星 为2则从卫星邻居
    private int Retrieving_Flag;
    private int Content_ID;
    private final int AreaID;
    private Area UserArea;
    private  Satellite Serving_Satellite;
    public final static HashMap<Integer,TerrestrialUsers> All_Users =new HashMap<>();
    private final List<Tuple<Content,Integer>> User_Requested_Content=new ArrayList<>();



    public TerrestrialUsers(int ID,int zipcode,int AreaID){
        this.UserId=ID;
        this.Zipcode=zipcode;
        this.AreaID=AreaID;
    }

    //用户初始化函数，计算用户所在地区
    public void init(){
        this.UserArea = All_Areas.get(this.AreaID);
    }
    public int getUserId(){
        return this.UserId;
    }

    public int getZipcode(){
        return this.Zipcode;
    }

    public Area getUserArea(){
        return this.UserArea;
    }
    public int getAreaID(){
        return this.AreaID;
    }

    public int getContentID(){
        return Content_ID;
    }

    public Satellite getServingSatellite(){
        return this.UserArea.getServingSatellite();
    }

    //用户发出对内容的请求
    public void RequestContent(Content content,Request request){
        Content_ID=content.getContentID();
        this.Serving_Satellite=getServingSatellite();
        Retrieving_Flag=100;

        if(isCooperative)
            Retrieving_Flag=this.Serving_Satellite.isCachedContentCooperative(content);
        else
            Retrieving_Flag=this.Serving_Satellite.isCachedContentDistributed(content);


        this.getUserArea().Request_Times++;
        if(Retrieving_Flag==1)
            this.getUserArea().Hitted_Request_Times++;
        else if(Retrieving_Flag!=100)
            this.getUserArea().Hitted_Request_Times_Neighbor++;

        //启用这种策略时，注意第一个周期是没有协作缓存区域的，从第二个周期开始才有
        //Retrieving_Flag=this.Serving_Satellite.isCachedContentNeighborhood(content);

        Tuple<Content,Integer> temp=new Tuple<>(content,Retrieving_Flag);
        //加入请求的获取情况，从哪里获取的flag
        this.User_Requested_Content.add(temp);
        //服务该地区的卫星记录该请求
        this.Serving_Satellite.updateCache(content,request);
        //整个地区的内容获取情况也要更新
        this.UserArea.updateRequestedContent(content);
    }

    public double RetrieveContentDelay(){
        double Content_Retrieval_Delay;
        if(Retrieving_Flag==100)
            Content_Retrieval_Delay=Delay_Retrieve_Content_From_Terrestrial;
        else{
            int hopsbetweenss=Retrieving_Flag-1;
            Content_Retrieval_Delay= Delay_Retrieve_Content_From_Satellite+hopsbetweenss*Delay_Retrieve_Content_From_Satellite_Neighbor;
        }

        return Content_Retrieval_Delay;
    }

    //返回某个用户取回内容的平均延迟
    public double getRetrieveAllContentAverageDelay(){
        double averagedelay=0;
        for(Tuple<Content,Integer> temp:User_Requested_Content){
            int flag=temp.getValue();
            if(flag==100){
                averagedelay+=Delay_Retrieve_Content_From_Terrestrial;
            }
            else{
                int hopsbetweenss=Retrieving_Flag-1;
                averagedelay+= Delay_Retrieve_Content_From_Satellite+hopsbetweenss*Delay_Retrieve_Content_From_Satellite_Neighbor;
            }
        }
        return averagedelay/User_Requested_Content.size();
    }

    //用于计算得到该地区用户获取内容的平均延迟
    public double getUserRetrieveTotalDelay(){
        double totaldelay=0;
        if(User_Requested_Content.size()==0)return 0;
        for(Tuple<Content,Integer> temp:User_Requested_Content){
            int flag=temp.getValue();
            if(flag==100){
                totaldelay+=Delay_Retrieve_Content_From_Terrestrial;
            }
            else{
                int hopsbetweenss=flag-1;
                totaldelay+= Delay_Retrieve_Content_From_Satellite+hopsbetweenss*Delay_Retrieve_Content_From_Satellite_Neighbor;
            }
        }
        return totaldelay;
    }

    public List<Tuple<Content,Integer>> getUserRequestedContent(){
        return this.User_Requested_Content;
    };
}
