package org.owasp.esapi.filters.waf.actions;

public class DoNothingAction extends Action {

	public boolean failedRule() {
		return this.failed;
	}


	public boolean isActionNecessary() {
		return false;
	}

}
