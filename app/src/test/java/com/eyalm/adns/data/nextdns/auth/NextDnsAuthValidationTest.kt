package com.eyalm.adns.data.nextdns.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDnsAuthValidationTest {
    @Test
    fun `two factor code requires exactly six digits`() {
        assertTrue(isValidTwoFactorCode("123456"))
        assertFalse(isValidTwoFactorCode("12345"))
        assertFalse(isValidTwoFactorCode("1234567"))
        assertFalse(isValidTwoFactorCode("12345a"))
        assertFalse(isValidTwoFactorCode(""))
    }

    @Test
    fun `successful JSON login response is accepted`() {
        assertEquals(
            PasswordLoginResponse.Accepted,
            classifyPasswordLoginResponse(
                status = 200,
                raw = "{}",
                submittedCode = false,
            ),
        )
    }

    @Test
    fun `plain text OK login response is accepted`() {
        assertEquals(
            PasswordLoginResponse.Accepted,
            classifyPasswordLoginResponse(
                status = 200,
                raw = "OK",
                submittedCode = false,
            ),
        )
    }

    @Test
    fun `HTTP 200 password error is mapped to password field`() {
        assertEquals(
            PasswordLoginResponse.Outcome(
                NextDnsLoginOutcome.Failure(
                    NextDnsLoginFailure.InvalidCredentials,
                    NextDnsLoginField.Password,
                )
            ),
            classifyPasswordLoginResponse(
                status = 200,
                raw = """{"errors":{"password":"invalidCombination"}}""",
                submittedCode = false,
            ),
        )
    }

    @Test
    fun `invalid credentials are displayed on email and password fields`() {
        val failure = NextDnsLoginOutcome.Failure(
            NextDnsLoginFailure.InvalidCredentials,
            NextDnsLoginField.Password,
        )

        assertEquals(
            mapOf(
                NextDnsLoginField.Email to NextDnsLoginFailure.InvalidCredentials,
                NextDnsLoginField.Password to NextDnsLoginFailure.InvalidCredentials,
            ),
            failure.fieldErrors(NextDnsLoginMode.Password),
        )
    }

    @Test
    fun `non credential failures remain attached to their original field`() {
        val failure = NextDnsLoginOutcome.Failure(
            NextDnsLoginFailure.InvalidTwoFactorCode,
            NextDnsLoginField.Code,
        )

        assertEquals(
            mapOf(NextDnsLoginField.Code to NextDnsLoginFailure.InvalidTwoFactorCode),
            failure.fieldErrors(NextDnsLoginMode.Password),
        )
    }

    @Test
    fun `HTTP 200 two factor challenge is mapped before success`() {
        assertEquals(
            PasswordLoginResponse.Outcome(NextDnsLoginOutcome.RequiresTwoFactor),
            classifyPasswordLoginResponse(
                status = 200,
                raw = "{\"requiresCode\":true}",
                submittedCode = false,
            ),
        )
    }

    @Test
    fun `HTTP 200 two factor error is mapped to code field`() {
        assertEquals(
            PasswordLoginResponse.Outcome(
                NextDnsLoginOutcome.Failure(
                    NextDnsLoginFailure.InvalidTwoFactorCode,
                    NextDnsLoginField.Code,
                )
            ),
            classifyPasswordLoginResponse(
                status = 200,
                raw = """{"errors":{"code":"incorrect"}}""",
                submittedCode = true,
            ),
        )
    }

    @Test
    fun `malformed errors are not accepted as a successful login`() {
        assertEquals(
            PasswordLoginResponse.Outcome(
                NextDnsLoginOutcome.Failure(NextDnsLoginFailure.Unknown)
            ),
            classifyPasswordLoginResponse(
                status = 200,
                raw = """{"errors":true}""",
                submittedCode = false,
            ),
        )
    }

    @Test
    fun `structured errors are mapped without raw text`() {

        assertEquals(
            NextDnsLoginOutcome.Failure(
                NextDnsLoginFailure.InvalidCredentials,
                NextDnsLoginField.Password,
            ),
            mapLoginFailure(
                status = 400,
                raw = "{\"errors\":[{\"code\":\"invalidCombination\"}]}",
                submittedCode = false,
            ),
        )
        assertEquals(
            NextDnsLoginOutcome.Failure(
                NextDnsLoginFailure.InvalidTwoFactorCode,
                NextDnsLoginField.Code,
            ),
            mapLoginFailure(
                status = 401,
                raw = "not-json",
                submittedCode = true,
            ),
        )
        assertEquals(
            NextDnsLoginFailure.ServerUnavailable,
            mapLoginFailure(
                status = 503,
                raw = "",
                submittedCode = false,
            ).reason,
        )
    }
}
