import { test, expect } from "@playwright/test";

/**
 * The one required end-to-end flow for this feature (test-strategy.md):
 * create a ticket, ask the assistant about it, see it cited, click
 * through to the ticket — run against the real Docker Compose stack
 * (frontend + backend + Elasticsearch + Ollama), not mocks.
 */
test("create ticket → ask assistant about it → see citation → click through to ticket detail", async ({
  page,
  context,
}) => {
  const uniqueAssignee = `e2e.ask.${Date.now()}`;
  const distinctiveText = `zzqbxk-payment-gateway-timeout-${Date.now()}`;

  // --- Create a ticket with distinctive content ---
  await page.goto("/tickets/new");
  await page.getByLabel("Title").fill(`E2E ask-and-cite ${distinctiveText}`);
  await page
    .getByLabel("Description")
    .fill(`Investigating ${distinctiveText} seen during checkout.`);
  await page.getByLabel("Priority").selectOption("MEDIUM");
  await page.getByLabel("Assignee").fill(uniqueAssignee);
  await page.getByRole("button", { name: /create ticket/i }).click();

  await page.waitForURL(/\/tickets\/[0-9a-f-]{36}$/, { timeout: 15000 });
  const ticketId = page.url().match(/\/tickets\/([0-9a-f-]{36})$/)?.[1];
  expect(ticketId).toBeTruthy();

  // --- Navigate to the AI Q&A panel via the nav shell (not a direct URL, FR-009) ---
  await page.getByRole("link", { name: /ask assistant/i }).click();
  await page.waitForURL(/\/assistant$/);

  // --- Ask about it. Ingestion runs asynchronously after creation, so retry
  //     briefly if the assistant hasn't indexed the new ticket yet. ---
  const question = `Have we seen ${distinctiveText} before?`;
  let citationLink = page.getByRole("link", { name: ticketId! });

  for (let attempt = 0; attempt < 5; attempt++) {
    await page.getByLabel("Ask a question").fill(question);
    await page.getByRole("button", { name: /^ask$/i }).click();
    await expect(page.getByRole("button", { name: /^ask$/i })).toBeEnabled({ timeout: 20000 });

    if (await citationLink.isVisible().catch(() => false)) {
      break;
    }
    await page.waitForTimeout(3000);
  }

  await expect(citationLink).toBeVisible({ timeout: 5000 });
  await expect(citationLink).toHaveAttribute("target", "_blank");

  // --- Click the citation: opens the ticket detail in a new tab, panel untouched ---
  const [detailPage] = await Promise.all([context.waitForEvent("page"), citationLink.click()]);
  await detailPage.waitForLoadState();

  await expect(detailPage).toHaveURL(new RegExp(`/tickets/${ticketId}$`));
  await expect(
    detailPage.getByRole("heading", { name: `E2E ask-and-cite ${distinctiveText}` }),
  ).toBeVisible();

  // Original /assistant tab is unaffected.
  await expect(page).toHaveURL(/\/assistant$/);
  await expect(citationLink).toBeVisible();
});
