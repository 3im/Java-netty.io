
import com.sun.org.apache.bcel.internal.classfile.StackMap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.ConcurrentSet;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by 3im on 15.04.2015.
 */
public class ReceivedRequestHandler extends SimpleChannelInboundHandler<Object> {

    private static AtomicInteger uniqueRequests = new AtomicInteger(0);
    private static Set<Request> uniqueReqSet = new HashSet<Request>();
    private static ConcurrentHashMap<String, Integer> redirectCount = new ConcurrentHashMap<String, Integer>();
    private static HashMap<String, Request> reqStat = new HashMap<String, Request>();
    private static Stack<Request> lastSixteenRequests = new Stack<Request>();
    private static AtomicInteger currentConnections = new AtomicInteger(0);
    private static InternalLogger logger = InternalLoggerFactory.getInstance("logger");

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg.toString().equals("EmptyLastHttpContent") || msg.toString().contains("favicon")){return;}
        Request request = new Request(msg);
        request.setRequestTime(request.getDate().getTime());
        request.setIp(ctx.channel().remoteAddress().toString().split(":")[0].substring(1));
        lastSixteenRequests.push(request);
        synchronized (reqStat) {
            if (reqStat.containsKey(request.getIp())) {
                request.setReqCount(reqStat.get(request.getIp()).getReqCount() + 1);
                reqStat.replace(request.getIp(), request);

            } else {
                reqStat.put(request.getIp(), request);
            }
        }
        switch (request.getType()){
            case GREETINGS: sayHello(ctx, request);
                break;
            case REDIRECT: makeRedirect(ctx, request);
                break;
            case STATUS: showStatus(ctx, request);
                break;
            case EMPTY: emptyRequest(ctx);
                break;
        }

        if (uniqueReqSet.add(request)) {
            logger.info("\nREQUEST TYPE: " + request.getType()
                    + "\nUNIQUE REQUEST ADDED, COUNT: " + uniqueRequests.incrementAndGet()
                    + "\nUNIQUE REQUEST FROM IP: " + request.getIp() + "\n");
        }
        request.setRequestTime(new Date().getTime() - request.getRequestTime());
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        currentConnections.getAndIncrement();

        logger.info("\nChannel was REGISTERED\nCurrent connections count: " + currentConnections.get() + "\n");

    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

        super.channelUnregistered(ctx);
        currentConnections.getAndDecrement();
        logger.info("\nChannel was UNREGISTERED\nCurrent connections count: " + currentConnections.get() + "\n");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    private void sayHello(final ChannelHandlerContext ctx, Request r) throws Exception{
        r.setReceivedBytes("hello world".length()*2);
        TimeUnit.SECONDS.sleep(10);
        ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("hello world".getBytes()))).addListener(ChannelFutureListener.CLOSE);
    }
    private void emptyRequest(final ChannelHandlerContext ctx){
        DefaultHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("Make direct request:/status /hello /redirect?url=<url>".getBytes()));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    private void makeRedirect(final ChannelHandlerContext ctx, final Request r){

        if (!(r.getMessage() instanceof  HttpRequest)){
            return;
        }

        DefaultHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        String url = r.getUri().split("=")[1];

        if (redirectCount.containsKey(url)){
            redirectCount.replace(url, redirectCount.get(url) + 1);
        } else {
            redirectCount.put(url, 1);
        }

        url = url.toLowerCase().startsWith("http") ? url : "http://" + url;
        response.headers().set(HttpHeaderNames.LOCATION, url);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void showStatus(final ChannelHandlerContext ctx, Request r){

        StringBuilder builder = new StringBuilder();
        String tableBuilder = tableBuilder();
        builder.append("<!DOCTYPE html>" +
                "<html lang=\"en\">"+
                "<head><title>Status</title></head>"+
                "<body>"+
                "<center><h3>Stat info:</h3></center>"+
                tableBuilder +
                "</body>" +
                "</html>");
        r.setReceivedBytes(builder.length()*2);
        DefaultHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(builder.toString().getBytes()));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

    }

    private String tableBuilder(){
        StringBuilder builder = new StringBuilder();
        builder.append("<style type=\"text/css\">\n" +
                ".tg  {border-collapse:collapse;border-spacing:0;margin:0px auto;}\n" +
                ".tg td{font-family:Arial, sans-serif;font-size:14px;padding:10px 5px;border-style:solid;border-width:1px;overflow:hidden;word-break:normal;}\n" +
                ".tg th{font-family:Arial, sans-serif;font-size:14px;font-weight:normal;padding:10px 5px;text-align:center;background-color:#c0c0c0;border-style:solid;border-width:1px;overflow:hidden;word-break:normal;}\n" +
                "</style>\n" +
                "<table class=\"tg\">\n" +
                "  <tr>\n" +
                "    <th class=\"tg-031e\" colspan=\"2\">Open connections:" + currentConnections.toString() +
                "    <br>Unique requests:" + uniqueRequests.toString() +
                "    </th>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td class=\"tg-031e\">" +
                "<table class=\"tg\">\n" +
                "  <tr>\n" +
                "    <th class=\"tg-031e\">IP</th>\n" +
                "    <th class=\"tg-031e\">COUNT</th>\n" +
                "    <th class=\"tg-031e\">TIME</th>\n" +
                "  </tr>\n");
        //1st Table
        for (Map.Entry<String, Request> entry : reqStat.entrySet())
        {
            builder.append("<tr><td class=\"tg-031e\">"+ entry.getKey() +"</td>" +
                    "    <td class=\"tg-031e\">"+ entry.getValue().getReqCount() +"</td>"+
                    "    <td class=\"tg-031e\">"+ entry.getValue().getFormatDate() +"</td></tr>");
        }
        //2nd Table
        builder.append("</table></td>\n" +
                "    <td class=\"tg-031e\">" +
                "<table class=\"tg\">\n" +
                "  <tr>\n" +
                "    <th class=\"tg-031e\">URL</th>\n" +
                "    <th class=\"tg-031e\">REDIRECT COUNT</th>\n" +
                "  </tr>\n");
        for (Map.Entry<String, Integer> entry : redirectCount.entrySet())
        {
            builder.append("<tr><td class=\"tg-031e\">"+ entry.getKey() +"</td>" +
                    "    <td class=\"tg-031e\" colspan=\"2\">"+ entry.getValue() +"</td></tr>");
        }


        //3rd Table
        builder.append("</table></td>\n" +
                "  </tr>\n"+
                "<td class=\"tg-031e\" colspan=\"2\"><table class=\"tg\">\n" +
                "  <tr>\n" +
                "    <th class=\"tg-031e\">IP</th>\n" +
                "    <th class=\"tg-031e\">URI</th>\n" +
                "    <th class=\"tg-031e\">TIMESTAMP</th>\n" +
                "    <th class=\"tg-031e\">SENT BYTES</th>\n" +
                "    <th class=\"tg-031e\">RECEIVED BYTES</th>\n" +
                "    <th class=\"tg-031e\">SPEED(B/SEC)</th>\n" +
                "  </td></tr>\n");
        for (int i = 0; i<15; i++) {
            if (lastSixteenRequests.size()<i+1){
                break;
            }
            Request r = lastSixteenRequests.get(lastSixteenRequests.size()-1-i);
                builder.append("  <tr>\n" +
                        "    <td class=\"tg-031e\">" + r.getIp() + "</th>\n" +
                        "    <td class=\"tg-031e\">" + r.getUri() + "</th>\n" +
                        "    <td class=\"tg-031e\">" + r.getFormatDate() + "</th>\n" +
                        "    <td class=\"tg-031e\">" + r.getSendBytes() + "</th>\n" +
                        "    <td class=\"tg-031e\">" + r.getReceivedBytes() + "</th>\n" +
                        "    <td class=\"tg-031e\">" + r.getSpeed() + "</th>\n" +
                        "  </tr>\n");

        }
        builder.append("  </table>\n" +
                "</table>");
        return builder.toString();
    }
}
