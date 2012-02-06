/**
 * Copyright (c) 2006, James Seigel, Calgary, AB., Canada
 * Copyright (c) 2003,2004, Stefan Haustein, Oberhausen, Rhld., Germany
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The  above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE. 
 */

package org.ksoap2.transport;

import java.util.List;
import java.io.*;
import java.net.Proxy;

import org.ksoap2.*;
import org.ksoap2.multipart.SimpleMimeReader;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;
import org.kxml2.io.*;
import org.xmlpull.v1.*;

/**
 * Abstract class which holds common methods and members that are used by the
 * transport layers. This class encapsulates the serialization and
 * deserialization of the soap messages, leaving the basic communication
 * routines to the subclasses.
 */
abstract public class Transport {

    /**
     * Added to enable web service interactions on the emulator
     * to be debugged with Fiddler2 (Windows) but provides utility
     * for other proxy requirements.
     */
    protected Proxy proxy;
    protected String url;
    protected int timeout = ServiceConnection.DEFAULT_TIMEOUT;
    /** Set to true if debugging */
    public boolean debug;
    /** String dump of request for debugging. */
    public String requestDump;
    /** String dump of response for debugging */
    public String responseDump;
    private String xmlVersionTag = "";

    /** Indicate whether the message is multipart */
    private boolean multipart = false;

    protected static final String CONTENT_TYPE_XML_CHARSET_UTF_8 = "text/xml;charset=utf-8";
    protected static final String CONTENT_TYPE_SOAP_XML_CHARSET_UTF_8 = "application/soap+xml;charset=utf-8";
    protected static final String USER_AGENT = "ksoap2-android/2.6.0+";

    public Transport() {
    }

    public Transport(String url) {
        this(null, url);
    }

    public Transport(String url, int timeout) {
        this.url = url;
        this.timeout = timeout;
    }

    /**
     * Construct the transport object
     *
     * @param proxy Specifies the proxy server to use for 
     * accessing the web service or <code>null</code> if a direct connection is available
     * @param url Specifies the web service url
     *
     */
    public Transport(Proxy proxy, String url) {
        this.proxy = proxy;
        this.url = url;
    }

    public Transport(Proxy proxy, String url, int timeout) {
        this.proxy = proxy;
        this.url = url;
        this.timeout = timeout;
    }

    /**
     * Check if the current call contains multipart input
     * @return whether the current transport is for multipart input
     */
    public boolean isMultipart() {
        return multipart;
    }

    /**
     * Set if the current transport is for a multipart input
     * @param multipart boolean indicating whether the message is multipart or not
     */
    public void setMultipart(boolean multipart) {
        this.multipart = multipart;
    }

    /**
     * Setup multipart mime parseing
     */
    protected void parseMultipartResponse(SoapEnvelope envelope, InputStream is)
            throws XmlPullParserException, IOException {
        SimpleMimeReader mimeReader = new SimpleMimeReader(is);
        String boundary = mimeReader.getBoundaryText();
        if (boundary ==  null || "".equals(boundary)) {
            throw new IOException("Please check if this is a valid multipart message");
        }

        byte[] message = mimeReader.getPreambleBytes();
        // Send this to the regular parseresponse object
        parseResponse(envelope, new ByteArrayInputStream(message));

        // Read the Attachment Data (only the first one for now)
        mimeReader.nextPart();
        byte[] attachmentData = mimeReader.getPartDataAsBytes();

        // Extract the bodyIn from the envelope and stuff the attachmentData into it as well
        Object bodyIn = envelope.bodyIn;
        if (bodyIn instanceof SoapFault)
		{
			throw (SoapFault) bodyIn;
		}
		KvmSerializable ks = (KvmSerializable) bodyIn;
        SoapObject object = new SoapObject("Namespace", "Body");
        PropertyInfo bodyProperty = new PropertyInfo();
        bodyProperty.setName("body");
        bodyProperty.setValue(ks.getProperty(0));
        bodyProperty.setType(SoapObject.class);
        object.addProperty(bodyProperty);

        SoapObject attachment = new SoapObject("Namespace", "fileattachment");
        PropertyInfo attachmentProperty = new PropertyInfo();
        attachmentProperty.setName("attachmentData");
        attachmentProperty.setValue(attachmentData);
        attachmentProperty.setType(byte[].class);
        object.addProperty(attachmentProperty);

        // Reset the bodyIn to be this "SOAPObject" that contains the message and attachment
        envelope.bodyIn = object;
    }

    /**
     * Sets up the parsing to hand over to the envelope to deserialize.
     */
    protected void parseResponse(SoapEnvelope envelope, InputStream is) throws XmlPullParserException, IOException {
        XmlPullParser xp = new KXmlParser();
        xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        xp.setInput(is, null);
        envelope.parse(xp);
    }

    /**
     * Serializes the request.
     */
    protected byte[] createRequestData(SoapEnvelope envelope) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(xmlVersionTag.getBytes());
        XmlSerializer xw = new KXmlSerializer();
        xw.setOutput(bos, null);
        envelope.write(xw);
        xw.flush();
        bos.write('\r');
        bos.write('\n');
        bos.flush();
        return bos.toByteArray();
    }

    /**
     * Set the target url.
     *
     * @param url
     *            the target url.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets the version tag for the outgoing soap call. Example <?xml
     * version=\"1.0\" encoding=\"UTF-8\"?>
     *
     * @param tag
     *            the xml string to set at the top of the soap message.
     */
    public void setXmlVersionTag(String tag) {
        xmlVersionTag = tag;
    }

    /**
     * Attempts to reset the connection.
     */
    public void reset() {
    }

    /**
     * Perform a soap call with a given namespace and the given envelope providing
     * any extra headers that the user requires such as cookies. Headers that are
     * returned by the web service will be returned to the caller in the form of a
     * <code>List</code> of <code>HeaderProperty</code> instances.
     *
     * @param targetNamespace
     *            the namespace with which to perform the call in.
     * @param envelope
     *            the envelope the contains the information for the call.
     * @param headers
     * 			  <code>List</code> of <code>HeaderProperty</code> headers to send with the SOAP request.
     *
     * @return Headers returned by the web service as a <code>List</code> of
     * <code>HeaderProperty</code> instances.
     */
    abstract public List call(String targetNamespace, SoapEnvelope envelope, List headers) throws IOException, XmlPullParserException;

    /**
     * Perform a soap call with a given namespace and the given envelope.
     *
     * @param targetNamespace
     *            the namespace with which to perform the call in.
     * @param envelope
     *            the envelope the contains the information for the call.
     */
    public void call(String targetNamespace, SoapEnvelope envelope) throws IOException, XmlPullParserException {
        call(targetNamespace, envelope, null);
    }

    /**
     * Return the name of the host that is specified as the web service target
     *
     * @return Host name
     */
    abstract public String getHost();

    /**
     * Return the port number of the host that is specified as the web service target
     *
     * @return Port number
     */
    abstract public int getPort();

    /**
     * Return the path to the web service target
     *
     * @return The URL's path
     */
    abstract public String getPath();
}
