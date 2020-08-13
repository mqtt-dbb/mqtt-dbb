package it.unipr.netsec.coap;


/** CoAP content formats.
 */
public class CoapContentFormats {

	/** Format text/plain;charset=utf-8 [RFC7252] [RFC2046] [RFC3676] [RFC5147] */
	public static final int FORMAT_TEXT_PLAIN_UTF8=0;
	
	/** Format application/link-format [RFC7252] [RFC6690] */
	public static final int FORMAT_APP_LINK_FORMAT=40;
	
	/** Format application/xml [RFC7252] [RFC3023]] */
	public static final int FORMAT_APP_XML=41;

	/** Format application/octet-stream [RFC7252] [RFC2045] [RFC2046] */
	public static final int FORMAT_APP_OCTECT_STREAM=42;

	/** Format application/exi [RFC7252] [REC-exi-20140211] */
	public static final int FORMAT_APP_EXI=47;

	/** Format application/json [RFC7252] [RFC7159] */
	public static final int FORMAT_APP_JSON=50;

	/** Format application/ace+cbor [I-D.ietf-ace-oauth-authz] */
	public static final int FORMAT_APP_ACE_CBOR=19;

}
