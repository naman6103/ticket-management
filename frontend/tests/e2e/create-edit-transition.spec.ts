import { test, expect } from "@playwright/test";

/**
 * The one required end-to-end flow (test-strategy.md): create a ticket,
 * edit it, transition its status — run against the real Docker Compose
 * stack (frontend + backend + MySQL), not mocks.
 */
test("create ticket → edit it → transition its status", async ({ page }) => {
  const uniqueAssignee = `e2e.tester.${Date.now()}`;

  // --- Create ---
  await page.goto("/tickets/new");
  await page.getByLabel("Title").fill("E2E flow ticket");
  await page.getByLabel("Description").fill("Created by the required E2E flow");
  await page.getByLabel("Priority").selectOption("MEDIUM");
  await page.getByLabel("Assignee").fill(uniqueAssignee);
  await page.getByRole("button", { name: /create ticket/i }).click();

  await page.waitForURL(/\/tickets\/[0-9a-f-]{36}$/, { timeout: 15000 });

  await expect(page.getByRole("heading", { name: "E2E flow ticket" })).toBeVisible();
  await expect(page.getByTestId("status-badge")).toHaveText("OPEN");
  await expect(page.getByText("Created by the required E2E flow")).toBeVisible();
  await expect(page.getByText(uniqueAssignee)).toBeVisible();

  // --- Edit (title + priority only; description/assignee must stay put) ---
  await page.getByRole("button", { name: /^edit$/i }).click();

  const titleInput = page.getByLabel("Title");
  await titleInput.fill("");
  await titleInput.fill("E2E flow ticket (edited)");

  await page.getByLabel("Priority").selectOption("HIGH");
  await page.getByRole("button", { name: /save changes/i }).click();

  await expect(page.getByRole("heading", { name: "E2E flow ticket (edited)" })).toBeVisible();
  await expect(page.getByText("Created by the required E2E flow")).toBeVisible();
  await expect(page.getByText(uniqueAssignee)).toBeVisible();

  // --- Transition (OPEN -> IN_PROGRESS) ---
  await page.getByLabel("Transition status").selectOption("IN_PROGRESS");
  await page.getByRole("button", { name: /confirm/i }).click();

  await expect(page.getByTestId("status-badge")).toHaveText("IN_PROGRESS");

  const remainingOptions = await page
    .getByLabel("Transition status")
    .locator("option")
    .allTextContents();
  expect(remainingOptions).toEqual(["Select next status", "RESOLVED", "CANCELLED"]);
});
