package info.bottiger.podcast.parser;

import info.bottiger.podcast.provider.Subscription;
import info.bottiger.podcast.utils.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;

public class OPMLParserHandler extends DefaultHandler {
	

	
	private static final String NODE_HEAD = "head";
	private static final String NODE_OUTLINE = "outline";
	
	private static final String NODE_XML_URL = "xmlUrl";

	private final Log log = Log.getLog(getClass());
	
	public Context context;
	
	public int success_num;
	public int dup_num;;
	public int fail_num;;
	




	public OPMLParserHandler() {
		success_num = 0;
		dup_num = 0;
		fail_num = 0;
	}



	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		

	}

	void stopParse() throws SAXException {
		throw new SAXException("Stop parse!");
	}


	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {

		
		
		if (NODE_OUTLINE.equalsIgnoreCase(localName)) {
			String url = attributes.getValue(NODE_XML_URL);
			if(url!=null){
				Pattern p = Pattern.compile("^(http|https)://.*",
						Pattern.CASE_INSENSITIVE);
				Matcher m = p.matcher(url);	
				
				if (m.find()) {
					Subscription sub = new Subscription(url);
					
					int rc = sub.subscribe(context);
					if(rc == Subscription.ADD_FAIL_DUP){
						dup_num ++;
					}else if(rc == Subscription.ADD_SUCCESS){
						success_num ++;
					}else {
						fail_num ++;
					}					
				}				
			}
		
		} 

	}



}
