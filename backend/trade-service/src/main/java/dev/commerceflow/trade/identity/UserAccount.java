package dev.commerceflow.trade.identity;

public record UserAccount(long id, String loginName, String passwordHash, String role) {
    @Override public String toString() { return "UserAccount[id=" + id + ", role=" + role + "]"; }
}
