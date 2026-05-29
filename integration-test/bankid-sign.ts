import { chromium } from 'playwright'

const {
  BANKID_SIGN_URL,
  NATIONAL_IDENTITY_NUMBER,
  BANKID_TEST_OTP,
  BANKID_TEST_PASSWORD
} = process.env

if (BANKID_SIGN_URL === undefined) throw new Error('BANKID_SIGN_URL is not set')
if (NATIONAL_IDENTITY_NUMBER === undefined) throw new Error('NATIONAL_IDENTITY_NUMBER is not set')
if (BANKID_TEST_OTP === undefined) throw new Error('BANKID_TEST_OTP is not set')
if (BANKID_TEST_PASSWORD === undefined) throw new Error('BANKID_TEST_PASSWORD is not set')

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()

try {
  // Step 1: Load the WYSIWYS page and open the document
  console.log('Navigating to sign URL...')
  await page.goto(BANKID_SIGN_URL)
  await page.locator('#js_read-document').click()

  // Step 2: Click Sign (visible after document opens)
  console.log('Clicking Sign...')
  await page.locator('#js_sign_now').waitFor({ state: 'visible' })
  await page.locator('#js_sign_now').click()

  // Step 3: Enter national identity number
  console.log('Entering NIN...')
  await page.locator('input[inputmode="numeric"]').waitFor({ state: 'visible' })
  await page.locator('input[inputmode="numeric"]').fill(NATIONAL_IDENTITY_NUMBER)
  await page.getByRole('button', { name: 'Neste' }).click()

  // Steps 4 and 5 are inside an iframe hosted by BankID
  const bankidFrame = page.frameLocator('iframe[title="BankID"]')

  // Step 4: Enter one-time code
  console.log('Entering OTP...')
  await bankidFrame.getByLabel('Skriv inn engangskoden din').waitFor({ state: 'visible' })
  await bankidFrame.getByLabel('Skriv inn engangskoden din').fill(BANKID_TEST_OTP)
  await bankidFrame.getByRole('button', { name: 'Neste' }).click()

  // Step 5: Enter BankID password
  console.log('Entering password...')
  await bankidFrame.getByLabel('Ditt BankID-passord').waitFor({ state: 'visible' })
  await bankidFrame.getByLabel('Ditt BankID-passord').fill(BANKID_TEST_PASSWORD)
  await bankidFrame.getByRole('button', { name: 'Neste' }).click()

  // Step 6: Accept terms and confirm signing
  console.log('Accepting terms and confirming...')
  await page.locator('#privacy-terms-checkbox').waitFor({ state: 'visible' })
  await page.locator('#privacy-terms-checkbox').check()
  await page.locator('form#consent-confirm').getByRole('button', { name: 'Sign' }).click()

  // Step 7: Wait for completion
  console.log('Waiting for signing to complete...')
  // The /oppsummering page first shows a "please wait" spinner, then the final status.
  await page.waitForFunction(
    () => !document.body.innerText.includes('vennligst vent'),
    { timeout: 60_000 }
  )
  // Success: the summary page ("Oppsummering") is shown with the document list.
  // Failure: a different heading / error message would appear.
  const bodyText: string = await page.locator('body').innerText()
  if (!bodyText.includes('Oppsummering')) {
    const headings: string[] = await page.locator('h1, h2, h3').allInnerTexts()
    throw new Error(`Unexpected completion page. Headings: ${headings.join(' | ')}`)
  }
  console.log('BankID signing completed successfully.')
} catch (err) {
  console.error('BankID signing failed:', (err as Error).message)
  await browser.close()
  process.exit(1)
}

await browser.close()
