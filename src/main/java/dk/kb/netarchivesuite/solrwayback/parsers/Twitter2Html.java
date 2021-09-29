package dk.kb.netarchivesuite.solrwayback.parsers;

import dk.kb.netarchivesuite.solrwayback.facade.Facade;
import dk.kb.netarchivesuite.solrwayback.properties.PropertiesLoader;
import dk.kb.netarchivesuite.solrwayback.properties.PropertiesLoaderWeb;
import dk.kb.netarchivesuite.solrwayback.service.dto.ArcEntryDescriptor;
import dk.kb.netarchivesuite.solrwayback.service.dto.ImageUrl;
import dk.kb.netarchivesuite.solrwayback.solr.NetarchiveSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;

public class Twitter2Html {
    private static final Logger log = LoggerFactory.getLogger(Twitter2Html.class);
    public static String twitter2Html(String jsonString, String crawlDate) throws Exception{
        StringBuilder b = new StringBuilder();
        TwitterParser2 parser = new TwitterParser2(jsonString);
        String image_icons = PropertiesLoader.WAYBACK_BASEURL+"images/twitter_sprite.png";

        String textReplaced = newline2Br(parser.getText());
        //TODO frontend fix so all other params not needed
        String otherSearchParams=" AND type%3A\"Twitter Tweet\"&start=0&filter=&imgsearch=false&imggeosearch=false&grouping=false";
        textReplaced = replaceHashTags(PropertiesLoaderWeb.WAYBACK_SERVER, otherSearchParams, textReplaced, parser.getHashTags());

        String title;
        String type;
        if (parser.isRetweet()){
            type=", retweeted @" + parser.getOriginalAuthor() + ":";
            title ="Retweet by: "+ parser.getAuthor();
        }
        else {
            type=", tweeted:";
            title ="Tweet by: "+ parser.getAuthor();
        }
        ArrayList<String> images_norm = new ArrayList<String>();

        for (String img : parser.getImageUrlsList()){
            images_norm.add(Normalisation.canonicaliseURL(img));
        }

        String queryStr = Facade.queryStringForImages(images_norm);
        ArrayList<ArcEntryDescriptor> images = NetarchiveSolrClient.getInstance().findImagesForTimestamp(queryStr, crawlDate);

        String user_image = parser.getProfileImage();
        String user_image_norm =(Normalisation.canonicaliseURL(user_image));

        ArrayList<String> user_image_list = new ArrayList<String>();
        user_image_list.add(user_image_norm);

        String queryStrUser = Facade.queryStringForImages( user_image_list);
        ArrayList<ArcEntryDescriptor> images_user = NetarchiveSolrClient.getInstance().findImagesForTimestamp(queryStrUser,crawlDate);//Only 1


        ArrayList<ImageUrl> imageUrls = Facade.arcEntrys2Images(images);
        ArrayList<ImageUrl> imageUrl_user = Facade.arcEntrys2Images(images_user);

        String css =
                "body {background: #f3f3f6;color: #333333;font-family: Arial, Helvetica, sans-serif;margin: 0;}"+
                "#wrapper {"+
                "  background: white;margin: 0 auto; padding: 2em; max-width: 1000px;}"+
                "h2 {"+
                "  margin: 5px 0 0; font-size: 18px;"+
                "}"+
                ".tweet {"+
                "  border: 1px solid #cccccc; line-height: 1.6em;overflow: hidden; padding: 1em;"+
                "}"+
                ".item {"+
                "  padding: .5em 0;"+
                "}"+
                ".item .image {display: block;margin-top: 1em;max-width: 600px;"+
                "}"+
                ".item .image img {"+
                "  max-width: 100%;"+
                "}"+
                ".item.reactions span {"+
                "  vertical-align: middle;"+
                "}"+
                ".item.reactions span.icon { display: inline-block; height: 20px; width: 20px;"+
                "}"+
                ".item.reactions span.number {"+
                "  display: inline-block;"+
                "  margin-right: 1.5em;"+
                "}"+
                ".item.reactions span.replies {"+
                " background: transparent url("+image_icons+") no-repeat -145px -50px;"+
                "}"+
                ".item.reactions span.retweets {"+
                " background: transparent url("+image_icons+") no-repeat -180px -50px;"+
                "}"+
                ".item.reactions span.likes {"+
                " background: transparent url("+image_icons+") no-repeat -145px -130px;"+
                "}"+
                ".avatar{"+
                "float: left;"+
                "margin-right: 1em;"+
                "}"+
                ".avatar img{"+
                "   border-radius: 50%;"+
                "}"+
                ".item.date{"+
                "clear: both;"+
                "}";

        String html =
                "<!DOCTYPE html>"+
                "<html>"+
                "<head>"+
                  "<meta http-equiv='Content-Type' content='text/html;charset=UTF-8'>"+
                  "<meta name='viewport' content='width=device-width, initial-scale=1'>"+
                  "<title>"+title+"</title>"+
                  "<style>"+css+"</style>"+
                "</head>"+
                "<body>"+
                  "<div id='wrapper'>"+
                    "<div class='tweet'>"+
                    "<span class='avatar'>"+
                    imagesHtml(imageUrl_user)+
                    "</span>"+
                      "<div class='item author'>"+
                        "<h2>"+parser.getAuthor()+ type+"</h2>"+
                      "</div>"+
                      "<div class='item date'>"+
                        "<div>"+parser.getCreatedDate()+"</div>"+
                      "</div>"+
                      "<div class='item text'>"+
                       textReplaced+
                        "<span class='image'>"+
                          imagesHtml(imageUrls)+
                        "</span>"+
                      "</div>"+
                      "<div class='item reactions'>"+
                        "<span class='icon retweets'></span>"+
                        "<span class='number'>"+parser.getNumberOfRetweets()+"</span>"+
                        "<span class='icon likes'></span>"+
                        "<span class='number'>"+parser.getNumberOfLikes()+"</span>"+ //#retweets are not the JSON (only for premium subscribers)
                      "</div>"+
                    "</div>"+
                  "</div>"+
                "</body>"+
                "</html>";

        return html;
    }


    /*HashTags are in clear text without # in front.
     * Replace this with a link that searches for the tag.
     *
     */
    public static String replaceHashTags(String solrwaybackUrl, String otherSearchParams, String text, HashSet<String> tags) {
        log.info("tags replace called for text: '"+text +"' with tags:"+tags);
        for (String tag : tags) {
            log.info("replacing tag:"+tag);
            String link = solrwaybackUrl+"?query=keywords%3A"+tag+otherSearchParams;
            String tagWithLink = "<span><a href='"+link+"'>#"+tag+"</a></span>";
            text = text.replaceAll("#" + tag, tagWithLink);
        }
        return text;
    }


    private static String newline2Br(String text){
        if (text==null){
            return "";
        }
        return text.replace("\n","<br>");

    }

    public static String imagesHtml(ArrayList<ImageUrl> images){
        StringBuilder b = new StringBuilder();
        for (ImageUrl image : images){
            b.append("<img src='"+ image.getDownloadUrl()+"' />\n");
        }
        return b.toString();
    }
}
