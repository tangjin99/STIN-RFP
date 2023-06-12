package ContentFiles;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Content implements Serializable {
    private final int Content_ID;
    private final String Content_Feature;
    public static final List<Content> All_Contents =new ArrayList<>();
    public static final int Content_Feature_Dimension=19;
    private double[] Content_Feature_Array=new double[19];
    public Content(int id,String feature){
        this.Content_ID=id;
        this.Content_Feature=feature;
        this.Content_Feature_Array=getStringToArray(Content_Feature);

    }

    public int getContentID(){
        return this.Content_ID;
    }

    public String getContentFeature(){
        return this.Content_Feature;
    }
    public double[] getContentFeatureArray(){
        return this.Content_Feature_Array;
    }

    public double[] getStringToArray(String s){
        double[] res=new double[s.length()];
        for(int i=0;i<s.length();i++){
            res[i]=s.charAt(i)-'0';
        }
        return res;
    }

}
