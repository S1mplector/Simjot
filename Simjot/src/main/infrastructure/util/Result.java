/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.util;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Result type for robust error handling without exceptions.
 * Represents either a success value or an error message.
 * 
 * @param <T> The type of the success value
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    /**
     * Create a success result.
     */
    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Create a failure result.
     */
    static <T> Result<T> failure(String error) {
        return new Failure<>(error);
    }

    /**
     * Create a failure result from an exception.
     */
    static <T> Result<T> failure(Throwable ex) {
        return new Failure<>(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
    }

    /**
     * Wrap a potentially throwing operation.
     */
    static <T> Result<T> of(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Wrap a potentially throwing runnable.
     */
    static Result<Void> run(Runnable runnable) {
        try {
            runnable.run();
            return success(null);
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Check if this is a success.
     */
    boolean isSuccess();

    /**
     * Check if this is a failure.
     */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Get the value if success, or throw if failure.
     */
    T getOrThrow();

    /**
     * Get the value if success, or return default if failure.
     */
    T getOrElse(T defaultValue);

    /**
     * Get the value if success, or compute default if failure.
     */
    T getOrElseGet(Supplier<T> supplier);

    /**
     * Get the error message if failure.
     */
    Optional<String> getError();

    /**
     * Get the value as Optional.
     */
    Optional<T> toOptional();

    /**
     * Transform the value if success.
     */
    <U> Result<U> map(Function<T, U> mapper);

    /**
     * Transform the value if success, flattening nested Results.
     */
    <U> Result<U> flatMap(Function<T, Result<U>> mapper);

    /**
     * Execute action if success.
     */
    Result<T> onSuccess(Consumer<T> action);

    /**
     * Execute action if failure.
     */
    Result<T> onFailure(Consumer<String> action);

    /**
     * Recover from failure with a new value.
     */
    Result<T> recover(Function<String, T> recovery);

    /**
     * Recover from failure with a new Result.
     */
    Result<T> recoverWith(Function<String, Result<T>> recovery);

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    record Success<T>(T value) implements Result<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getOrThrow() {
            return value;
        }

        @Override
        public T getOrElse(T defaultValue) {
            return value;
        }

        @Override
        public T getOrElseGet(Supplier<T> supplier) {
            return value;
        }

        @Override
        public Optional<String> getError() {
            return Optional.empty();
        }

        @Override
        public Optional<T> toOptional() {
            return Optional.ofNullable(value);
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            Objects.requireNonNull(mapper);
            try {
                return success(mapper.apply(value));
            } catch (Exception e) {
                return failure(e);
            }
        }

        @Override
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            Objects.requireNonNull(mapper);
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return failure(e);
            }
        }

        @Override
        public Result<T> onSuccess(Consumer<T> action) {
            Objects.requireNonNull(action);
            action.accept(value);
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<String> action) {
            return this;
        }

        @Override
        public Result<T> recover(Function<String, T> recovery) {
            return this;
        }

        @Override
        public Result<T> recoverWith(Function<String, Result<T>> recovery) {
            return this;
        }
    }

    record Failure<T>(String error) implements Result<T> {
        public Failure {
            Objects.requireNonNull(error, "error must not be null");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T getOrThrow() {
            throw new IllegalStateException("Result is failure: " + error);
        }

        @Override
        public T getOrElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        public T getOrElseGet(Supplier<T> supplier) {
            Objects.requireNonNull(supplier);
            return supplier.get();
        }

        @Override
        public Optional<String> getError() {
            return Optional.of(error);
        }

        @Override
        public Optional<T> toOptional() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> map(Function<T, U> mapper) {
            return (Result<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            return (Result<U>) this;
        }

        @Override
        public Result<T> onSuccess(Consumer<T> action) {
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<String> action) {
            Objects.requireNonNull(action);
            action.accept(error);
            return this;
        }

        @Override
        public Result<T> recover(Function<String, T> recovery) {
            Objects.requireNonNull(recovery);
            try {
                return success(recovery.apply(error));
            } catch (Exception e) {
                return failure(e);
            }
        }

        @Override
        public Result<T> recoverWith(Function<String, Result<T>> recovery) {
            Objects.requireNonNull(recovery);
            try {
                return recovery.apply(error);
            } catch (Exception e) {
                return failure(e);
            }
        }
    }
}
