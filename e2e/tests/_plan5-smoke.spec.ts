import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('admin geo-rules y promotions cargan', async ({ page }) => {
  await login(page, 'demo_admin');
  await page.goto('/admin/geo-rules');
  await expect(page.locator('h1')).toContainText('Reglas geográficas');
  await page.goto('/admin/promotions');
  await expect(page.locator('h1')).toContainText('Promociones y descuentos');
});
