package org.owasp.esapi.filters.waf.rules;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.owasp.esapi.filters.waf.actions.Action;
import org.owasp.esapi.filters.waf.configuration.AppGuardianConfiguration;

import org.owasp.esapi.filters.waf.internal.InterceptingHTTPServletResponse;

public abstract class Rule {

	protected String id = "(no rule ID)";
	protected static Logger logger = Logger.getLogger(Rule.class);

	public abstract Action check( HttpServletRequest request, InterceptingHTTPServletResponse response );

	public void log( HttpServletRequest request, String message ) {

		logger.log(AppGuardianConfiguration.LOG_LEVEL,
				"[IP=" + request.getRemoteAddr() +
				",Rule=" + this.getClass().getSimpleName() + "] " + message);

	}

	protected void setId(String id) {
		if ( id == null || "".equals(id) )
			return;

		this.id = id;
	}

}
