/**
 * OWASP Enterprise Security API (ESAPI)
 * 
 * This file is part of the Open Web Application Security Project (OWASP)
 * Enterprise Security API (ESAPI) project. For details, please see
 * http://www.owasp.org/esapi.
 *
 * Copyright (c) 2007 - The OWASP Foundation
 * 
 * The ESAPI is published by OWASP under the LGPL. You should read and accept the
 * LICENSE before you use, modify, and/or redistribute this software.
 * 
 * @author Jeff Williams <a href="http://www.aspectsecurity.com">Aspect Security</a>
 * @created 2007
 */
package org.owasp.esapi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.owasp.esapi.errors.IntrusionException;
import org.owasp.esapi.errors.ValidationAvailabilityException;
import org.owasp.esapi.errors.ValidationException;
import org.owasp.validator.html.AntiSamy;
import org.owasp.validator.html.CleanResults;
import org.owasp.validator.html.PolicyException;
import org.owasp.validator.html.ScanException;

/**
 * Reference implementation of the IValidator interface. This implementation
 * relies on the ESAPI Encoder, Java Pattern (regex), Date,
 * and several other classes to provide basic validation functions. This library
 * has a heavy emphasis on whitelist validation and canonicalization. All double-encoded
 * characters, even in multiple encoding schemes, such as <PRE>&amp;lt;</PRE> or
 * <PRE>%26lt;<PRE> or even <PRE>%25%26lt;</PRE> are disallowed.
 * 
 * @author Jeff Williams (jeff.williams .at. aspectsecurity.com) <a
 *         href="http://www.aspectsecurity.com">Aspect Security</a>
 * @since June 1, 2007
 * @see org.owasp.esapi.interfaces.IValidator
 */
public class Validator implements org.owasp.esapi.interfaces.IValidator {

	/** The instance. */
	private static Validator instance = new Validator();

	/** The logger. */
	private static final Logger logger = Logger.getLogger("ESAPI", "Validator");
	
	
	/**
	 * Gets the single instance of Validator.
	 * 
	 * @return single instance of Validator
	 */
	public static Validator getInstance() {
		return instance;
	}

	/**
	 * Hide the constructor for the Singleton pattern.
	 */
	private Validator() {
		// hidden
	}

	/**
	 * Validates data received from the browser and returns a safe version. Only
	 * URL encoding is supported. Double encoding is treated as an attack.
	 * 
	 * @param name
	 * @param type
	 * @param input
	 * @return
	 * @throws ValidationException
	 */
	public String getValidDataFromBrowser(String name, String type, String input) throws ValidationException {
		String canonical = Encoder.getInstance().canonicalize( input );
		
		if ( input == null )
			throw new ValidationException("Bad input", type + " (" + name + ") to validate was null" );
		
		if ( type == null )
			throw new ValidationException("Bad input", type + " (" + name + ") to validate against was null" );
		
		Pattern p = SecurityConfiguration.getInstance().getValidationPattern( type );
		if ( p == null )
			throw new ValidationException("Bad input", type + " (" + name + ") to validate against not configured in ESAPI.properties" );
				
		if ( !p.matcher(canonical).matches() )
			throw new ValidationException("Bad input", type + " (" + name + "=" + input + ") did not match pattern " + p );
		
		// if everything passed, then return the canonical form
		return canonical;
	}

	/**
	 * Returns true if data received from browser is valid. Only URL encoding is
	 * supported. Double encoding is treated as an attack.
	 * 
	 * @param name
	 * @param type
	 * @param value
	 * @return
	 */
	public boolean isValidDataFromBrowser(String name, String type, String value) {
		try {
			getValidDataFromBrowser(name, type, value);
			return true;
		} catch( Exception e ) {
			return false;
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#getValidDate(java.lang.String)
	 */
	public Date getValidDate(String context, String input, DateFormat format) throws ValidationException {
		try {
			Date date = format.parse(input);
			return date;
		} catch (Exception e) {
			throw new ValidationException( "Invalid date", "Problem parsing date (" + context + "=" + input + ") ",e );
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidCreditCard(java.lang.String)
	 */
	public boolean isValidCreditCard(String name, String value) {
		try {
			String canonical = getValidDataFromBrowser(name, "CreditCard", value);
			
			// perform Luhn algorithm checking
			StringBuffer digitsOnly = new StringBuffer();
			char c;
			for (int i = 0; i < canonical.length(); i++) {
				c = canonical.charAt(i);
				if (Character.isDigit(c)) {
					digitsOnly.append(c);
				}
			}
		
			int sum = 0;
			int digit = 0;
			int addend = 0;
			boolean timesTwo = false;
		
			for (int i = digitsOnly.length() - 1; i >= 0; i--) {
				digit = Integer.parseInt(digitsOnly.substring(i, i + 1));
				if (timesTwo) {
					addend = digit * 2;
					if (addend > 9) {
						addend -= 9;
					}
				} else {
					addend = digit;
				}
				sum += addend;
				timesTwo = !timesTwo;
			}
		
			int modulus = sum % 10;
			return modulus == 0;
		} catch( Exception e ) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Returns true if the directory path (not including a filename) is valid.
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidDirectoryPath(java.lang.String)
	 */
	public boolean isValidDirectoryPath(String name, String dirpath) {
		String canonical = Encoder.getInstance().canonicalize(dirpath);
		
		try {
			// get the canonical path without the drive letter if present
			String cpath = new File(canonical).getCanonicalPath().replaceAll("\\\\", "/");
			String temp = cpath.toLowerCase();
			if (temp.length() >= 2 && temp.charAt(0) >= 'a' && temp.charAt(0) <= 'z' && temp.charAt(1) == ':') {
				cpath = cpath.substring(2);
			}

			// prepare the input without the drive letter if present
			String escaped = canonical.replaceAll("\\\\", "/");
			temp = escaped.toLowerCase();
			if (temp.length() >= 2 && temp.charAt(0) >= 'a' && temp.charAt(0) <= 'z' && temp.charAt(1) == ':') {
				escaped = escaped.substring(2);
			}

			// the path is valid if the input matches the canonical path
			return escaped.equals(cpath.toLowerCase());
		} catch (IOException e) {
			return false;
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidFileUpload(java.lang.String,java.lang.String,byte[]
	 *      content)
	 */
	public boolean isValidFileContent(String context, byte[] content) {
		// FIXME: AAA - temporary - what makes file content valid? Maybe need a parameter here?
		long length = SecurityConfiguration.getInstance().getAllowedFileUploadSize();
		return (content.length < length);
		// FIXME: log something?
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidFileName(java.lang.String)
	 */
	
	//FIXME: AAA - getValidFileName eliminates %00 and other injections.
	//FIXME: AAA - this method should check for %00 injection too
	public boolean isValidFileName(String context, String input) {
		if (input == null || input.length() == 0)
			return false;

		String canonical = Encoder.getInstance().canonicalize(input);

		// detect path manipulation
		try {
			File f = new File(canonical);
			String c = f.getCanonicalPath();
			String cpath = c.substring(c.lastIndexOf(File.separator) + 1);
			if (!input.equals(cpath)) {
				// FIXME: AAA should this validation really throw an IntrusionException?
				throw new IntrusionException("Invalid filename", "Invalid filename (" + canonical + ") doesn't match canonical path (" + cpath + ") and could be an injection attack");
			}
		} catch (IOException e) {
			throw new IntrusionException("Invalid filename", "Exception during filename validation", e);
		}

		// verify extensions
		List extensions = SecurityConfiguration.getInstance().getAllowedFileExtensions();
		Iterator i = extensions.iterator();
		while (i.hasNext()) {
			String ext = (String) i.next();
			if (input.toLowerCase().endsWith(ext.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidFileUpload(java.lang.String,
	 *      java.lang.String, byte[])
	 */
	public boolean isValidFileUpload(String context, String filepath, String filename, byte[] content) {
		return isValidDirectoryPath(context, filepath) && isValidFileName(context, filename) && isValidFileContent(context, content);
	}

	/**
	 * Validate the parameters, cookies, and headers in an HTTP request against
	 * specific regular expressions defined in the SecurityConfiguration. Note
	 * that isValidDataFromBrowser uses the Encoder.canonicalize() method to ensure
	 * that all encoded characters are reduced to their simplest form, and that any
	 * double-encoded characters are detected and throw an exception.
	 */
	public boolean isValidHTTPRequest(HttpServletRequest request) {
		boolean result = true;

		Iterator i1 = request.getParameterMap().entrySet().iterator();
		while (i1.hasNext()) {
			Map.Entry entry = (Map.Entry) i1.next();
			String name = (String) entry.getKey();
			if ( !isValidDataFromBrowser( "http", "HTTPParameterName", name ) ) {
				// logger.logCritical(Logger.SECURITY, "Parameter name (" + name + ") violates global rule" );
				result = false;
			}

			String[] values = (String[]) entry.getValue();
			Iterator i3 = Arrays.asList(values).iterator();
			// FIXME:Enhance - consider throwing an exception if there are multiple parameters with the same name
			while (i3.hasNext()) {
				String value = (String) i3.next();
				if ( !isValidDataFromBrowser( name, "HTTPParameterValue", value ) ) {
					// logger.logCritical(Logger.SECURITY, "Parameter value (" + name + "=" + value + ") violates global rule" );
					result = false;
				}
			}
		}

		if (request.getCookies() != null) {
			Iterator i2 = Arrays.asList(request.getCookies()).iterator();
			while (i2.hasNext()) {
				Cookie cookie = (Cookie) i2.next();
				String name = cookie.getName();
				if ( !isValidDataFromBrowser( "http", "HTTPCookieName", name ) ) {
					// logger.logCritical(Logger.SECURITY, "Cookie name (" + name + ") violates global rule" );
					result = false;
				}

				String value = cookie.getValue();
				if ( !isValidDataFromBrowser( name, "HTTPCookieValue", value ) ) {
					// logger.logCritical(Logger.SECURITY, "Cookie value (" + name + "=" + value + ") violates global rule" );
					result = false;
				}
			}
		}

		Enumeration e = request.getHeaderNames();
		while (e.hasMoreElements()) {
			String name = (String) e.nextElement();
			if (name != null && !name.equalsIgnoreCase("Cookie")) {
				if ( !isValidDataFromBrowser( "http", "HTTPHeaderName", name ) ) {
					// logger.logCritical(Logger.SECURITY, "Header name (" + name + ") violates global rule" );
					result = false;
				}
				
				Enumeration e2 = request.getHeaders(name);
				while (e2.hasMoreElements()) {
					String value = (String) e2.nextElement();
					if ( !isValidDataFromBrowser( name, "HTTPHeaderValue", value ) ) {
						// logger.logCritical(Logger.SECURITY, "Header value (" + name + "=" + value + ") violates global rule" );
						result = false;
					}
				}
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidListItem(java.util.List,
	 *      java.lang.String)
	 */
	public boolean isValidListItem(List list, String value) {
		return list.contains(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidNumber(java.lang.String)
	 */
	public boolean isValidNumber(String input) {
		try {
			Double.parseDouble(input);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidParameterSet(java.util.Set,
	 *      java.util.Set, java.util.Set)
	 */
	public boolean isValidParameterSet(Set requiredNames, Set optionalNames) {
		HttpServletRequest request = Authenticator.getInstance().getCurrentRequest();
		Set actualNames = request.getParameterMap().keySet();
		
		Set missing = new HashSet(requiredNames);
		missing.removeAll(actualNames);
		if (missing.size() > 0) {
			return false;
		}
		Set extra = new HashSet(actualNames);
		extra.removeAll(requiredNames);
		extra.removeAll(optionalNames);
		if (extra.size() > 0) {
			return false;
		}
		return true;
	}

	/**
	 * Checks that all bytes are valid ASCII characters (between 33 and 126
	 * inclusive). This implementation does no decoding. http://en.wikipedia.org/wiki/ASCII. (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidASCIIFileContent(byte[])
	 */
	public boolean isValidPrintable(byte[] input) {
		for (int i = 0; i < input.length; i++) {
			if (input[i] < 33 || input[i] > 126)
				return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see org.owasp.esapi.interfaces.IValidator#isValidPrintable(java.lang.String)
	 */
	public boolean isValidPrintable(String input) {
		String canonical = Encoder.getInstance().canonicalize(input);
		return isValidPrintable(canonical.getBytes());
	}

	/**
	 * (non-Javadoc).
	 * 
	 * @param location
	 *            the location
	 * @return true, if is valid redirect location
	 * @see org.owasp.esapi.interfaces.IValidator#isValidRedirectLocation(String
	 *      location)
	 */
	public boolean isValidRedirectLocation(String context, String location) {
		// FIXME: ENHANCE - it's too hard to put valid locations in as regex
		return Validator.getInstance().isValidDataFromBrowser(context, "Redirect", location);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#isValidSafeHTML(java.lang.String)
	 */
	public boolean isValidSafeHTML(String name, String input) {
		String canonical = Encoder.getInstance().canonicalize(input);
		// FIXME: AAA this is just a simple blacklist test - will use Anti-SAMY
		return !canonical.contains("<scri") && !canonical.contains("onload");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.owasp.esapi.interfaces.IValidator#getValidSafeHTML(java.lang.String)
	 */
	public String getValidSafeHTML( String context, String input ) throws ValidationException {
		AntiSamy as = new AntiSamy();
		try {
			CleanResults test = as.scan(input);
			// OutputFormat format = new OutputFormat(test.getCleanXMLDocumentFragment().getOwnerDocument());
			// format.setLineWidth(65);
			// format.setIndenting(true);
			// format.setIndent(2);
			// format.setEncoding(AntiSamyDOMScanner.ENCODING_ALGORITHM);
			return(test.getCleanHTML().trim());
		} catch (ScanException e) {
			throw new ValidationException( "Invalid HTML", "Problem parsing HTML (" + context + "=" + input + ") ",e );
		} catch (PolicyException e) {
			throw new ValidationException( "Invalid HTML", "HTML violates policy (" + context + "=" + input + ") ",e );
		}
	}

	
	/**
	 * This implementation reads until a newline or the specified number of
	 * characters.
	 * 
	 * @param in
	 *            the in
	 * @param max
	 *            the max
	 * @return the string
	 * @throws ValidationException
	 *             the validation exception
	 * @see org.owasp.esapi.interfaces.IValidator#safeReadLine(java.io.InputStream,
	 *      int)
	 */
	public String safeReadLine(InputStream in, int max) throws ValidationException {
		if (max <= 0)
			throw new ValidationAvailabilityException("Invalid input", "Must read a positive number of bytes from the stream");

		StringBuffer sb = new StringBuffer();
		int count = 0;
		int c;

		try {
			while ((c = in.read()) != -1) {
				sb.append((char) c);
				count++;
				if (count > max)
					throw new ValidationAvailabilityException("Invalid input", "Read more than maximum characters allowed (" + max + ")");
				if (c == '\n')
					break;
			}
			return sb.toString();
		} catch (IOException e) {
			throw new ValidationAvailabilityException("Invalid input", "Problem reading from input stream", e);
		}
	}

}