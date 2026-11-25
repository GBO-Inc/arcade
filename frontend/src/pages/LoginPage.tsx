import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { Button, Card, TextField } from '../components/ui';
import { useAuth } from '../hooks/auth';
import { useSingleSubmit } from '../hooks/useSingleSubmit';
import {
  serverFieldErrors,
  validateLogin,
  type FieldErrors,
  type LoginField,
} from './authForms';

const LOGIN_FIELDS: readonly LoginField[] = ['username', 'password'];
// Shown for every 401 so the page never hints which of the two was wrong.
const WRONG_LOGIN_MESSAGE = 'Invalid username or password';

function loginErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status === 401 ? WRONG_LOGIN_MESSAGE : error.message;
  }
  return 'Unable to sign in';
}

export function LoginPage() {
  const { login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { submitting, run } = useSingleSubmit();
  const [fieldErrors, setFieldErrors] = useState<FieldErrors<LoginField>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const from = (
    location.state as {
      from?: { pathname?: string; search?: string; hash?: string };
    } | null
  )?.from;

  async function signIn(username: string, password: string) {
    try {
      await login(username, password);
      navigate(
        from
          ? `${from.pathname ?? '/'}${from.search ?? ''}${from.hash ?? ''}`
          : '/lobby',
        { replace: true },
      );
    } catch (error) {
      const serverErrors =
        error instanceof ApiError && error.status === 400
          ? serverFieldErrors(error, LOGIN_FIELDS)
          : {};
      setFieldErrors(serverErrors);
      if (Object.keys(serverErrors).length === 0) {
        setFormError(loginErrorMessage(error));
      }
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const values = {
      username: String(formData.get('username')),
      password: String(formData.get('password')),
    };
    const errors = validateLogin(values);
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }
    void run(() => signIn(values.username, values.password));
  }

  return (
    <Card className="mx-auto max-w-sm">
      <form className="space-y-ui-4" onSubmit={handleSubmit} noValidate>
        <h1 className="text-ui-xl font-bold">Login</h1>
        <TextField
          label="Username"
          name="username"
          autoComplete="username"
          error={fieldErrors.username}
        />
        <TextField
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          error={fieldErrors.password}
        />
        {formError && (
          <p role="alert" className="text-ui-sm text-ui-error">
            {formError}
          </p>
        )}
        <Button type="submit" loading={submitting}>
          Sign in
        </Button>
      </form>
    </Card>
  );
}
