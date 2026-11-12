package com.rusuh.app.feature.auth

/** The screen callbacks of the sign-in steps, sending intents to [viewModel]. [onDismiss] is the back arrow. */
fun signInActions(
    viewModel: SignInViewModel,
    onDismiss: () -> Unit = {},
): SignInActions =
    SignInActions(
        onEmailChange = { viewModel.dispatch(SignInIntent.EmailChanged(it)) },
        onSendCode = { viewModel.dispatch(SignInIntent.SendCode) },
        onCodeChange = { viewModel.dispatch(SignInIntent.CodeChanged(it)) },
        onCodeComplete = { viewModel.dispatch(SignInIntent.SubmitCode(it)) },
        onResendCode = { viewModel.dispatch(SignInIntent.ResendCode) },
        onChangeEmail = { viewModel.dispatch(SignInIntent.ChangeEmail) },
        onFullNameChange = { viewModel.dispatch(SignInIntent.NameChanged(it)) },
        onConsentChange = { viewModel.dispatch(SignInIntent.ConsentChanged(it)) },
        onCreateAccount = { viewModel.dispatch(SignInIntent.ContinueNewAccount) },
        onDismiss = onDismiss,
    )
