import type { ApiError } from '../api/client';

export type FieldErrors<K extends string> = Partial<Record<K, string>>;

// These limits must match RegisterRequest on the backend.
const USERNAME_LENGTH = { min: 3, max: 20 };
const EMAIL_MAX_LENGTH = 255;
const PASSWORD_LENGTH = { min: 8, max: 72 };

export interface RegisterValues {
  username: string;
  email: string;
  password: string;
  repeatPassword: string;
}

export type RegisterField = keyof RegisterValues;
export type LoginField = 'username' | 'password';

function isLengthBetween(value: string, { min, max }: typeof USERNAME_LENGTH) {
  return value.length >= min && value.length <= max;
}

export function validateRegister(
  values: RegisterValues,
): FieldErrors<RegisterField> {
  const errors: FieldErrors<RegisterField> = {};
  if (
    values.username.trim() === '' ||
    !isLengthBetween(values.username, USERNAME_LENGTH)
  ) {
    errors.username = 'Username must be 3 to 20 characters';
  }
  if (!/^[^\s@]+@[^\s@]+$/.test(values.email)) {
    errors.email = 'Enter a valid email address';
  } else if (values.email.length > EMAIL_MAX_LENGTH) {
    errors.email = 'Email must be at most 255 characters';
  }
  if (!isLengthBetween(values.password, PASSWORD_LENGTH)) {
    errors.password = 'Password must be 8 to 72 characters';
  }
  if (values.repeatPassword !== values.password) {
    errors.repeatPassword = 'Passwords do not match';
  }
  return errors;
}

export function validateLogin(values: {
  username: string;
  password: string;
}): FieldErrors<LoginField> {
  const errors: FieldErrors<LoginField> = {};
  if (values.username.trim() === '') {
    errors.username = 'Enter your username';
  }
  if (values.password === '') {
    errors.password = 'Enter your password';
  }
  return errors;
}

/** Picks the server messages for the given form fields out of a 400 response. */
export function serverFieldErrors<K extends string>(
  error: ApiError,
  fields: readonly K[],
): FieldErrors<K> {
  const errors: FieldErrors<K> = {};
  for (const field of fields) {
    const message = error.fieldError(field);
    if (message) {
      errors[field] = message;
    }
  }
  return errors;
}

/**
 * A 409 carries only a message, no field name, so the field is read from the text. A message
 * that names both (or neither) belongs to the whole form.
 */
export function conflictField(
  error: ApiError,
): 'username' | 'email' | undefined {
  const message = error.message.toLowerCase();
  const mentionsUsername = message.includes('username');
  const mentionsEmail = message.includes('email');
  if (mentionsUsername === mentionsEmail) {
    return undefined;
  }
  return mentionsUsername ? 'username' : 'email';
}
