package com.rusuh.app.domain.usecase

/** Base contract for a use case: one input [P], one output [R]. Use [Unit] for no input. */
fun interface CoreUseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}
