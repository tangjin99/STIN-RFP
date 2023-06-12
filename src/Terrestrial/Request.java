package Terrestrial;

import java.util.ArrayList;
import java.util.List;

public class Request implements Comparable<Object>{
    private final int UserID;
    private final int ContentID;
    private final int Request_Time;
    private final int Content_Rating;
    public static final List<Request> All_Requests =new ArrayList<>();

    public Request(int userid, int contentID, int content_Rating, int request_Time){
        this.UserID=userid;
        this.ContentID=contentID;
        this.Request_Time=request_Time;
        this.Content_Rating=content_Rating;
    }
    public int getUserID(){
        return this.UserID;
    }
    public int getContentID(){
        return this.ContentID;
    }

    public int getRequest_Time() {
        return Request_Time;
    }

    public int getContent_Rating() {
        return Content_Rating;
    }

    @Override
    public int compareTo(Object o) {
        Request A=(Request) o;
        int flag=-1;
        if (this.getRequest_Time() > A.getRequest_Time())
            flag = 1;
        if(this.getRequest_Time()==A.getRequest_Time())
            flag = 0;
        return flag;

    }
}
