import com.sun.org.apache.xpath.internal.SourceTree;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by 3im on 17.04.2015.
 */
public class Request{

    private Object message;
    private Date date;
    private int reqCount = 1;
    private long receivedBytes = 0;
    private long sendBytes = 0;

    private String uri;
    private String ip;
    private REQTYPE type;
    private long requestTime;
    public static enum REQTYPE{GREETINGS, REDIRECT, STATUS, EMPTY}
    private static final double hashCoefficient = Math.random();

    public Request(final Object message){
        this.message = message;
        this.date = new Date();
        this.sendBytes = message.toString().length()*2;
        this.uri = ((HttpRequest)message).uri();

        if (message.toString().contains("hello")){
            this.type = REQTYPE.GREETINGS;
        } else if (message.toString().contains("redirect")) {
            this.receivedBytes = uri.length()*2;
            this.type = REQTYPE.REDIRECT;
        } else if (message.toString().contains("status")){
            this.type = REQTYPE.STATUS;
        } else {
            this.type = REQTYPE.EMPTY;
            return;}

    }

    public void setIp(String ip){this.ip = ip;}

    public String getIp(){
        return ip;
    }

    public String getUri(){return uri;}

    public Date getDate(){
        return date;
    }

    public Object getMessage(){return this.message;}
    

    public String getFormatDate(){return new SimpleDateFormat("HH:mm:ss MM-dd-yyyy").format(this.date);}

    public REQTYPE getType(){
        return this.type;
    }

    public long getRequestTime() {
        return this.requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public void setReqCount(int i){
        this.reqCount = i;
    }

    public int getReqCount() {
        return this.reqCount;
    }

    public void setReceivedBytes(long l){
        this.receivedBytes = l;
    }

    public long getReceivedBytes(){
        return receivedBytes;
    }

    public long getSendBytes(){
        return sendBytes;
    }

    public double getSpeed(){
        return (this.requestTime>0) ? ((this.receivedBytes + this.sendBytes) / this.requestTime) : (this.receivedBytes + this.sendBytes);
    }
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Request)){
            return false;
        }
        Request comparable = (Request)obj;
        if (this.ip.equals(comparable.ip) && this.type.equals(comparable.getType())){
            return true;
        } else {return false;}
    }

    @Override
    public int hashCode() {
        double d = 1;
        for (char c : ip.toCharArray()){
            if (Character.isDigit(c)) {
                d *= ((Character.getNumericValue(c) + 1) * 1000 * hashCoefficient);
            }
        }
        d = d*type.toString().length();
        return (int)Double.doubleToLongBits(d);
    }
}
