import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { register } from '../api/auth';
import { ApiError } from '../api/client';
import { Button, Card, TextField } from '../components/ui';
import { useAuth } from '../hooks/auth';
import { useSingleSubmit } from '../hooks/useSingleSubmit';
import {
  conflictField,
  serverFieldErrors,
  validateRegister,
  type FieldErrors,
  type RegisterField,
  type RegisterValues,
} from './authForms';

const SERVER_FIELDS = ['username', 'email', 'password'] as const;

function readValues(form: HTMLFormElement): RegisterValues {
  const data = new FormData(form);
  return {
    username: String(data.get('username')),
    email: String(data.get('email')),
    password: String(data.get('password')),
    repeatPassword: String(data.get('repeatPassword')),
  };
}

export function RegisterPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { submitting, run } = useSingleSubmit();
  const [fieldErrors, setFieldErrors] = useState<FieldErrors<RegisterField>>(
    {},
  );
  const [formError, setFormError] = useState<string | null>(null);

  function showServerError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError('Unable to create the account');
      return;
    }
    const errors: FieldErrors<RegisterField> = serverFieldErrors(
      error,
      SERVER_FIELDS,
    );
    const conflict = error.status === 409 ? conflictField(error) : undefined;
    if (conflict) {
      errors[conflict] = error.message;
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length === 0) {
      setFormError(error.message);
    }
  }

  async function createAccount({ username, email, password }: RegisterValues) {
    try {
      await register({ username, email, password });
      await login(username, password);
      navigate('/lobby', { replace: true });
    } catch (error) {
      showServerError(error);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = readValues(event.currentTarget);
    const errors = validateRegister(values);
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }
    void run(() => createAccount(values));
  }

  return (
    <Card className="mx-auto max-w-sm">
      <form className="space-y-ui-4" onSubmit={handleSubmit} noValidate>
        <h1 className="text-ui-xl font-bold">Register</h1>
        <TextField
          label="Username"
          name="username"
          autoComplete="username"
          hint="3 to 20 characters"
          error={fieldErrors.username}
        />
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          error={fieldErrors.email}
        />
        <TextField
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          hint="8 to 72 characters"
          error={fieldErrors.password}
        />
        <TextField
          label="Repeat password"
          name="repeatPassword"
          type="password"
          autoComplete="new-password"
          error={fieldErrors.repeatPassword}
        />
        {formError && (
          <p role="alert" className="text-ui-sm text-ui-error">
            {formError}
          </p>
        )}
        <Button type="submit" loading={submitting}>
          Create account
        </Button>
      </form>
    </Card>
  );
}
