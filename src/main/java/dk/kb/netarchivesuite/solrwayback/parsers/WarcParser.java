package dk.kb.netarchivesuite.solrwayback.parsers;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.kb.netarchivesuite.solrwayback.service.dto.ArcEntry;

public class WarcParser extends  ArcWarcFileParserAbstract {

    private static final Logger log = LoggerFactory.getLogger(WarcParser.class);
    private static final String newLineChar ="\r\n";
    public static String WARC_HEADER_ENCODING ="ISO-8859-1";
    

    /*
     *Header example(notice the two different parts):
     *WARC/1.0
     *WARC-Type: response
     *WARC-Target-URI: http://www.boerkopcykler.dk/images/low_Trance-27.5-2-LTD-_20112013_151813.jpg
     *WARC-Date: 2014-02-03T18:18:53Z
     *WARC-Payload-Digest: sha1:C4HTYCUOGJ2PCQIKSRDAOCIDMFMFAWKK
     *WARC-IP-Address: 212.97.133.94
     *WARC-Record-ID: <urn:uuid:1068b604-f3d5-40b9-8aaf-7ed0df0a20b3>
     *Content-Type: application/http; msgtype=response
     *Content-Length: 7446
     *
     *HTTP/1.1 200 OK
     *Content-Type: image/jpeg
     *Last-Modified: Wed, 20 Nov 2013 14:18:21 GMT
     *Accept-Ranges: bytes
     *ETag: "8034965dfbe5ce1:0"
     *Server: Microsoft-IIS/7.0
     *X-Powered-By: ASP.NET
     *Date: Mon, 03 Feb 2014 18:18:53 GMT
     *Connection: close
     *Content-Length: 7178
     */
    public static ArcEntry getWarcEntry(String warcFilePath, long warcEntryPosition) throws Exception {
        RandomAccessFile raf=null;
        try{
        
          if (warcFilePath.endsWith(".gz")){ //It is zipped
             return getWarcEntryZipped(warcFilePath, warcEntryPosition);                       
          }
         
          ArcEntry warcEntry = new ArcEntry();
          StringBuffer headerLinesBuffer = new StringBuffer();
            raf = new RandomAccessFile(new File(warcFilePath), "r");
            raf.seek(warcEntryPosition);
                        
            String line = raf.readLine(); // First line
            headerLinesBuffer.append(line+newLineChar);
            
            if  (!(line.startsWith("WARC/"))) //No version check yet
            {            
                throw new IllegalArgumentException("WARC header is not WARC/'version', instead it is : "+line);
            }            
            
            while (!"".equals(line)) { // End of warc first header block is an empty line                
                line = raf.readLine();                
                headerLinesBuffer.append(line+newLineChar);
                populateWarcFirstHeader(warcEntry, line);                
            }
            
            long afterFirst = raf.getFilePointer(); //Now we are past the WARC header and back to the ARC standard 
            line = raf.readLine();  
            warcEntry.setStatus_code(getStatusCode(line));            
            headerLinesBuffer.append(line+newLineChar);
            while (!"".equals(line)) { // End of warc second header block is an empty line
                line = raf.readLine();                  
                headerLinesBuffer.append(line+"\r\n");
                populateWarcSecondHeader(warcEntry, line);
            }

            int totalSize= (int) warcEntry.getWarcEntryContentLength();            
            
            // Load the binary blog. We are now right after the header. Rest will be the binary
            long headerSize = raf.getFilePointer() - afterFirst;
            long binarySize = totalSize - headerSize;

            //log.debug("Warc entry : totalsize:"+totalSize +" headersize:"+headerSize+" binary size:"+binarySize);
                        
            byte[] bytes = new byte[(int) binarySize];
            raf.read(bytes);
            raf.close();
            warcEntry.setBinary(bytes);
            warcEntry.setHeader(headerLinesBuffer.toString());
            return warcEntry;
        }
        catch(Exception e){
            throw e;
        }
        finally {
            if (raf!= null){
                raf.close();
             }
        }
    }

    
    public static ArcEntry getWarcEntryZipped(String warcFilePath, long warcEntryPosition) throws Exception {
      RandomAccessFile raf=null;
      try{
           StringBuffer headerLinesBuffer = new StringBuffer();
            ArcEntry warcEntry = new ArcEntry();
            raf = new RandomAccessFile(new File(warcFilePath), "r");
            raf.seek(warcEntryPosition);
          
          //  log.info("file is zipped:"+warcFilePath);
            InputStream is = Channels.newInputStream(raf.getChannel());                           
            GZIPInputStream stream = new GZIPInputStream(is);             
           
            BufferedInputStream  bis= new BufferedInputStream(stream);
   
          String line = readLine(bis); // First line
          headerLinesBuffer.append(line+newLineChar);
          
          if  (!(line.startsWith("WARC/"))) //No version check yet
          {            
              throw new IllegalArgumentException("WARC header is not WARC/'version', instead it is : "+line);
          }            
          
          while (!"".equals(line)) { // End of warc first header block is an empty line
              line = readLine(bis);                
              headerLinesBuffer.append(line+newLineChar);
              populateWarcFirstHeader(warcEntry, line);             
              
          }

          int byteCount=0; //Bytes of second header
          
          LineAndByteCount lc =readLineCount(bis);
          line=lc.getLine();
          warcEntry.setStatus_code(getStatusCode(line));
          headerLinesBuffer.append(line+newLineChar);
          byteCount +=lc.getByteCount();                    

          while (!"".equals(line)) { // End of warc second header block is an empty line
              
            lc =readLineCount(bis);
            line=lc.getLine();
            headerLinesBuffer.append(line+newLineChar);
            byteCount +=lc.getByteCount();                    
                      
              populateWarcSecondHeader(warcEntry, line);
          
          }
          
          int totalSize= (int) warcEntry.getWarcEntryContentLength();
          int binarySize = totalSize-byteCount;
          
                    
                      
          //System.out.println("Warc entry : totalsize:"+totalSize +" binary size:"+binarySize +" firstHeadersize:"+byteCount);          
          byte[] chars = new byte[binarySize];           
          bis.read(chars);
          
          raf.close();
          bis.close();
          warcEntry.setBinary(chars); 
          warcEntry.setHeader(headerLinesBuffer.toString());
          /*
          System.out.println("-------- binary start");
          System.out.println(new String(chars));
          System.out.println("-------- slut");
  */
          return warcEntry;
      }
      catch(Exception e){
          throw e;
      }
      finally {
          if (raf!= null){
              raf.close();
           }
      }
  }

    
    
      public static String getWarcLastUrlPart(String warcHeaderLine) {        
        //Example:
        //WARC-Target-URI: http://www.boerkopcykler.dk/images/low_Trance-27.5-2-LTD-_20112013_151813.jpg
        String urlPath = warcHeaderLine.substring(16); // Skip WARC-Target-URI:                     
        String paths[] = urlPath.split("/");
        String fileName = paths[paths.length - 1];
        //log.debug("file:"+fileName +" was extracted from URL:"+warcHeaderLine);
        if (fileName == null){
            fileName="";
        }
        return fileName.trim();
    }

    private static String getWarcUrl(String warcHeaderLine) {        
        //Example:
        //WARC-Target-URI: http://www.boerkopcykler.dk/images/low_Trance-27.5-2-LTD-_20112013_151813.jpg
        String urlPath = warcHeaderLine.substring(16);                      
        return urlPath;
    }
    
    private static void populateWarcFirstHeader(ArcEntry warcEntry, String headerLine) {
        //log.debug("Parsing warc headerline(part 1):"+headerLine);                              
        if (headerLine.startsWith("WARC-Target-URI:")) {
            warcEntry.setFileName(getWarcLastUrlPart(headerLine));
            warcEntry.setUrl(getWarcUrl(headerLine));
        }    
        
        //Example:
        //Content-Length: 31131
        else if (headerLine.startsWith("Content-Length:")) {
            String[] contentLine = headerLine.split(" ");
            int totalSize = Integer.parseInt(contentLine[1].trim());               
            warcEntry.setWarcEntryContentLength(totalSize);                       
        }       
        
        else if (headerLine.startsWith("WARC-Date:")) {
            String[] contentLine = headerLine.split(" ");
             String crawlDate =contentLine[1].trim();  //Zulu time                                      
             warcEntry.setCrawlDate(crawlDate);
                                        
             Instant instant = Instant.parse (crawlDate);  //JAVA 8
             Date date = java.util.Date.from( instant );
             String waybackDate = date2waybackdate(date);             
             warcEntry.setWaybackDate(waybackDate);                          
        }
        
        
     }

     private static void populateWarcSecondHeader(ArcEntry warcEntry, String headerLine) {
        //  log.debug("parsing warc headerline(part 2):"+headerLine);                
          //Content-Type: image/jpeg
         // or Content-Type: text/html; charset=windows-1252          
       if (headerLine.toLowerCase().startsWith("content-type:")) {            
            String[] part1 = headerLine.split(":");
               String[] part2= part1[1].split(";");                        
               warcEntry.setContentType(part2[0].trim());          
               if (part2.length == 2){
                 String charset = part2[1].trim();
                 if (charset.startsWith("charset=")){                                   
                   String headerEncoding=charset.substring(8).replace("\"", ""); ////Some times Content-Type: text/html; charset="utf-8" instead of Content-Type: text/html; charset=utf-8
                   warcEntry.setContentCharset(charset.substring(8));
                 }                                   
               }
               
               
          }  //Content-Length: 31131
          else if (headerLine.toLowerCase().startsWith("content-length:")) {
            String[] contentLine = headerLine.split(" ");
              int totalSize = Integer.parseInt(contentLine[1].trim());               
              warcEntry.setContentLength(totalSize);                       
          }
          else if (headerLine.toLowerCase().startsWith("content-encoding:")) {
            String[] contentLine = headerLine.split(":");               
            warcEntry.setContentEncoding(contentLine[1].trim().replace("\"", "")); //Some times Content-Type: text/html; charset="utf-8" instead of Content-Type: text/html; charset=utf-8                       
          }
          else if (headerLine.toLowerCase().startsWith("location:")) {                                      
            warcEntry.setRedirectUrl(headerLine.substring(9).trim());
          }
                 
          
      }
     
     public static String readLine(BufferedInputStream  bis) throws Exception{
       ByteArrayOutputStream baos = new ByteArrayOutputStream();
       int current = 0; // CRLN || LN
       while ((current = bis.read()) != '\r' && current != '\n') {             
         baos.write((byte) current);  
       }
       if (current == '\r') {
        bis.read(); // line ends with 10 13        
       }
              
       return baos.toString(WARC_HEADER_ENCODING);
     }
     
     public static LineAndByteCount readLineCount(BufferedInputStream  bis) throws Exception{
       int count = 0;
       ByteArrayOutputStream baos = new ByteArrayOutputStream();
              
       int current = 0; // CRLN || LN
       
       count++; //Also count linefeed
       while ((current = bis.read()) != '\r' && current != '\n') {             
         baos.write((byte)current);       
         count++;
       }
       if (current == '\r') {
        bis.read(); // line ends with 10 13
        count++;
       }       
       LineAndByteCount lc = new LineAndByteCount();
       lc.setLine(baos.toString(WARC_HEADER_ENCODING));
       lc.setByteCount(count);

       return lc;
       
     }
    
     
     
     //TODO move to util class
       public static String  date2waybackdate(Date date) { 
       SimpleDateFormat dForm = new SimpleDateFormat("yyyyMMddHHmmss");        
       try {
       String waybackDate = dForm.format(date);
       return waybackDate;                              
       } 
       catch(Exception e){        
       log.error("Could not parse date:"+date,e);
       return "20170101010101"; //Default, should never happen.
       }
   }
    
}
