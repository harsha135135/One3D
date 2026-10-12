package store.one3d;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.support.CatalogFixtures;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.TestUsers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The V7 rules for files, requests, quotes and custom orders, enforced by
 * PostgreSQL itself: rows are written with plain SQL, bypassing the Java code.
 * {@code @Transactional} rolls each test back.
 */
@PostgresIntegrationTest
@Transactional
class CustomOrderConstraintsIT {

    @Autowired
    JdbcClient jdbc;

    private CatalogFixtures.Item catalogItem;

    @BeforeEach
    void createCatalogItem() {
        catalogItem = new CatalogFixtures(jdbc).orderableItem("V7 Constraint Cube", 10_000, "INR");
    }

    @Test
    void aRecipeBelongsToExactlyOneConfigurationOrQuote() {
        UUID quoteId = issuedQuote(newRequest());

        assertThatThrownBy(() -> insertRecipe(catalogItem.configurationId(), quoteId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("recipe_revision_one_owner");
    }

    @Test
    void aRecipeWithNoOwnerIsRefused() {
        assertThatThrownBy(() -> insertRecipe(null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("recipe_revision_one_owner");
    }

    @Test
    void aCatalogLineAndACustomLineEachNeedTheirOwnOwner() {
        UUID quoteId = issuedQuote(newRequest());
        UUID recipeId = insertRecipe(null, quoteId);

        assertThatThrownBy(() -> insertLine(newOrder(), "CUSTOM_QUOTE", catalogItem.configurationId(), quoteId, recipeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_source_matches_owner");
    }

    @Test
    void aCatalogLineCannotNameAQuote() {
        UUID quoteId = issuedQuote(newRequest());

        assertThatThrownBy(() -> insertLine(newOrder(), "CATALOG", catalogItem.configurationId(), quoteId,
                catalogItem.recipeRevisionId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_source_matches_owner");
    }

    @Test
    void aCustomLineMustUseTheRecipeOfItsOwnQuote() {
        UUID quoteA = issuedQuote(newRequest());
        UUID quoteB = issuedQuote(newRequest());
        insertRecipe(null, quoteA);
        UUID recipeOfB = insertRecipe(null, quoteB);

        assertThatThrownBy(() -> insertLine(newOrder(), "CUSTOM_QUOTE", null, quoteA, recipeOfB))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_recipe_of_quote_fk");
    }

    @Test
    void aCustomLineCannotUseACatalogRecipe() {
        UUID quoteId = issuedQuote(newRequest());

        assertThatThrownBy(() -> insertLine(newOrder(), "CUSTOM_QUOTE", null, quoteId, catalogItem.recipeRevisionId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_recipe_of_quote_fk");
    }

    @Test
    void aValidCustomLineIsAcceptedAndAQuoteHasAtMostOneOrder() {
        UUID quoteId = issuedQuote(newRequest());
        UUID recipeId = insertRecipe(null, quoteId);
        assertThatCode(() -> insertLine(newOrder(), "CUSTOM_QUOTE", null, quoteId, recipeId)).doesNotThrowAnyException();

        assertThatThrownBy(() -> insertLine(newOrder(), "CUSTOM_QUOTE", null, quoteId, recipeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("order_line_quote_unique");
    }

    @Test
    void aQuoteMustPriceARevisionOfItsOwnRequest() {
        UUID requestA = newRequest();
        UUID requestB = newRequest();

        assertThatThrownBy(() -> insertQuote(requestA, revisionOf(requestB), "ISSUED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("quote_revision_of_request_fk");
    }

    @Test
    void aRequestHasAtMostOneIssuedQuote() {
        UUID requestId = newRequest();
        issuedQuote(requestId);

        assertThatThrownBy(() -> insertQuote(requestId, revisionOf(requestId), "ISSUED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("quote_one_issued_per_request");
    }

    @Test
    void anAcceptedQuoteMustNameItsOrder() {
        UUID requestId = newRequest();

        assertThatThrownBy(() -> insertQuote(requestId, revisionOf(requestId), "ACCEPTED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("quote_accepted_has_order");
    }

    @Test
    void aFileBelongsToOneRevisionOnly() {
        UUID requestId = newRequest();
        UUID artifactId = jdbc.sql("SELECT artifact_id FROM submission_revision WHERE request_id = ?")
                .param(requestId).query(UUID.class).single();

        assertThatThrownBy(() -> insertRevision(requestId, 2, artifactId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("submission_revision_artifact_unique");
    }

    @Test
    void aFinalizedFileHasAPinnedVersionAndAVerifiedFileHasADigest() {
        assertThatThrownBy(() -> insertArtifact("VERIFYING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("artifact_version_pinned_after_upload");
    }

    @Test
    void aVerifiedFileWithoutADigestIsRefused() {
        assertThatThrownBy(() -> insertArtifact("VERIFIED", "v1", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("artifact_verified_has_digest");
    }

    @Test
    void workRefersToExactlyTheRecordItsKindNeeds() {
        UUID artifactId = insertArtifact("VERIFYING", "v1", null);
        UUID commandId = jdbc.sql("SELECT id FROM printer_command LIMIT 1").query(UUID.class).optional().orElse(null);

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO pending_work (id, kind, command_id, artifact_id, status, attempts, max_attempts,
                                                  available_at, created_at, updated_at)
                        VALUES (?, 'DISPATCH_START', ?, ?, 'PENDING', 0, 5, now(), now(), now())""")
                .params(UUID.randomUUID(), commandId, artifactId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pending_work_subject_matches_kind");
    }

    // --- rows written directly -----------------------------------------------

    private UUID insertArtifact(String status, String versionId, String sha256) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO artifact (id, owner_id, storage_key, original_filename, declared_size, status,
                                              version_id, sha256, verified_size, created_at, upload_expires_at)
                        VALUES (?, ?, ?, 'model.stl', 184, ?, ?, ?, ?, now(), now() + interval '15 minutes')""")
                .params(id, TestUsers.LEGACY_CUSTOMER_ID, "uploads/" + id, status, versionId, sha256,
                        sha256 == null ? null : 184L)
                .update();
        return id;
    }

    /** A SUBMITTED request with revision 1 and a verified file. */
    private UUID newRequest() {
        UUID requestId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO custom_request (id, customer_id, status, created_at, updated_at)
                        VALUES (?, ?, 'SUBMITTED', now(), now())""")
                .params(requestId, TestUsers.LEGACY_CUSTOMER_ID).update();
        insertRevision(requestId, 1, insertArtifact("VERIFIED", "v1", "a".repeat(64)));
        return requestId;
    }

    private void insertRevision(UUID requestId, int number, UUID artifactId) {
        jdbc.sql("""
                        INSERT INTO submission_revision (id, request_id, revision_number, artifact_id, material, colour,
                                                         notes, created_at)
                        VALUES (?, ?, ?, ?, 'PLA', 'White', '', now())""")
                .params(UUID.randomUUID(), requestId, number, artifactId).update();
    }

    private UUID revisionOf(UUID requestId) {
        return jdbc.sql("SELECT id FROM submission_revision WHERE request_id = ? ORDER BY revision_number DESC LIMIT 1")
                .param(requestId).query(UUID.class).single();
    }

    private UUID issuedQuote(UUID requestId) {
        return insertQuote(requestId, revisionOf(requestId), "ISSUED");
    }

    private UUID insertQuote(UUID requestId, UUID revisionId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO quote (id, request_id, revision_id, price_minor, currency, status, issued_at, expires_at)
                        VALUES (?, ?, ?, 50000, 'INR', ?, now(), now() + interval '7 days')""")
                .params(id, requestId, revisionId, status).update();
        return id;
    }

    private UUID insertRecipe(UUID configurationId, UUID quoteId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO recipe_revision (id, product_configuration_id, quote_id, revision_number, material,
                                                     layer_height_microns, notes)
                        VALUES (?, ?, ?, 99, 'PLA', 200, '')""")
                .params(id, configurationId, quoteId).update();
        return id;
    }

    private UUID newOrder() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO customer_order (id, customer_id, status, created_at, payment_window_ends_at)
                        VALUES (?, ?, 'AWAITING_PAYMENT', now(), now() + interval '72 hours')""")
                .params(id, TestUsers.LEGACY_CUSTOMER_ID).update();
        return id;
    }

    private void insertLine(UUID orderId, String source, UUID configurationId, UUID quoteId, UUID recipeId) {
        jdbc.sql("""
                        INSERT INTO order_line (id, order_id, source, product_configuration_id, quote_id,
                                                recipe_revision_id, product_name, configuration_code,
                                                configuration_description, recipe_revision_number, quantity,
                                                unit_price_minor, currency)
                        VALUES (?, ?, ?, ?, ?, ?, 'Custom part: model.stl', 'CUSTOM-test', 'PLA, White', 1, 1,
                                50000, 'INR')""")
                .params(UUID.randomUUID(), orderId, source, configurationId, quoteId, recipeId).update();
    }
}
