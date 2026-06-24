import static com.kms.katalon.core.checkpoint.CheckpointFactory.findCheckpoint
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject
import static com.kms.katalon.core.testobject.ObjectRepository.findWindowsObject
import com.kms.katalon.core.checkpoint.Checkpoint as Checkpoint
import com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords as CucumberKW
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testcase.TestCase as TestCase
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.testng.keyword.TestNGBuiltinKeywords as TestNGKW
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

WebUI.openBrowser(null)

WebUI.navigateToUrl('https://www.eduinnovax.com/login')

WebUI.click(findTestObject('Page_Login - UTM AAS/a_Forgot Password'))

WebUI.setText(findTestObject('Page_Forgot Password - UTM AAS/input_Email Address'), 'adminsqa@yopmail.com')

WebUI.click(findTestObject('Page_Forgot Password - UTM AAS/button_Request Reset Link'))

WebUI.newTab('')

WebUI.navigateToUrl('https://yopmail.com/')

WebUI.setText(findTestObject('Page_YOPmail - Disposable Email Address - Anonymous and temporary inbox/input_Enter your inbox here'), 
    'adminsqa@yopmail.com')

WebUI.click(findTestObject('Page_YOPmail - Disposable Email Address - Anonymous and temporary inbox/i_'))

WebUI.click(findTestObject('Page_Inbox/a_Reset My Password'))

WebUI.switchToWindowTitle('Reset Password - UTM AAS')

WebUI.setEncryptedText(findTestObject('Page_Reset Password - UTM AAS/input_New Password'), 'iGDxf8hSRT4=')

WebUI.setEncryptedText(findTestObject('Page_Reset Password - UTM AAS/input_Confirm New Password'), 'iGDxf8hSRT4=')

WebUI.click(findTestObject('Page_Reset Password - UTM AAS/button_Update Password'))

