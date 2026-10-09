package store.one3d.identity;

/** The two V1 roles, as Spring Security authority names. */
public final class Roles {

    /** Every signed-in person can place and view their own orders. */
    public static final String CUSTOMER = "ROLE_CUSTOMER";

    /** Members of the operator group can review, release and run production. */
    public static final String OPERATOR = "ROLE_OPERATOR";

    private Roles() {
    }
}
