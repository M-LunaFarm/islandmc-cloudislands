package kr.lunaf.cloudislands.coreservice.security;

record CoreApiAuthentication(boolean allowed, String nodeId, boolean nodeCredentialBindingConfigured) {
    static CoreApiAuthentication denied(boolean nodeCredentialBindingConfigured) {
        return new CoreApiAuthentication(false, "", nodeCredentialBindingConfigured);
    }

    static CoreApiAuthentication allowed(String nodeId, boolean nodeCredentialBindingConfigured) {
        return new CoreApiAuthentication(true, nodeId == null ? "" : nodeId.trim(), nodeCredentialBindingConfigured);
    }
}
