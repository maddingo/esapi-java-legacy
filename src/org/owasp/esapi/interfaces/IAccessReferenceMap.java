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
package org.owasp.esapi.interfaces;

import java.util.Iterator;

import org.owasp.esapi.errors.AccessControlException;

/**
 * The IAccessReferenceMap interface is used to map from a set of internal
 * direct object references to a set of indirect references that are safe to
 * disclose publically. This can be used to help protect database keys,
 * filenames, and other types of direct object references. As a rule, developers
 * should not expose their direct object references as it enables attackers to
 * attempt to manipulate them.
 * <P>
 * <img src="doc-files/AccessReferenceMap.jpg" height="600">
 * <P>
 * <P>
 * Indirect references are handled as strings, to facilitate their use in HTML.
 * Implementations can generate simple integers or more complicated random
 * character strings as indirect references. Implementations should probably add
 * a constructor that takes a list of direct references.
 * <P>
 * Note that in addition to defeating all forms of parameter tampering attacks,
 * there is a side benefit of the AccessReferenceMap. Using random strings as indirect object
 * references, as opposed to simple integers makes it impossible for an attacker to
 * guess valid identifiers. So if per-user AccessReferenceMaps are used, then request
 * forgery (CSRF) attacks will also be prevented.
 * 
 * <pre>
 * Set fileSet = new HashSet();
 * fileSet.addAll(...);
 * AccessReferenceMap map = new AccessReferenceMap( fileSet );
 * // store the map somewhere safe - like the session!
 * String indRef = map.getIndirectReference( file1 );
 * String href = &quot;http://www.aspectsecurity.com/esapi?file=&quot; + indRef );
 * ...
 * String indref = request.getParameter( &quot;file&quot; );
 * File file = (File)map.getDirectReference( indref );
 * </pre>
 * 
 * <P>
 * 
 * @author Jeff Williams (jeff.williams@aspectsecurity.com)
 */
public interface IAccessReferenceMap {

	/**
	 * Get an iterator through the direct object references.
	 * 
	 * @return the iterator
	 */
	Iterator iterator();

	/**
	 * Get a safe indirect reference to use in place of a potentially sensitive
	 * direct object reference. Developers should use this call when building
	 * URL's, form fields, hidden fields, etc... to help protect their private
	 * implementation information.
	 * 
	 * @param directReference
	 *            the direct reference
	 * 
	 * @return the indirect reference
	 */
	String getIndirectReference(Object directReference);

	/**
	 * Get the original direct object reference from an indirect reference.
	 * Developers should use this when they get an indirect reference from an
	 * HTTP request to translate it back into the real direct reference. If an
	 * invalid indirectReference is requested, then an AccessControlException is
	 * thrown.
	 * 
	 * @param indirectReference
	 *            the indirect reference
	 * 
	 * @return the direct reference
	 * 
	 * @throws AccessControlException
	 *             the access control exception
	 */
	Object getDirectReference(String indirectReference) throws AccessControlException;

	/**
	 * FIXME
	 * @param direct
	 */
	public void addDirectReference(String direct);
	
	/**
	 * FIXME
	 * @param direct
	 * @throws AccessControlException
	 */
	public void removeDirectReference(String direct) throws AccessControlException;

}