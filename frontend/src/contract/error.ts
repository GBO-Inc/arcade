/** Shared error shape for WebSocket and API errors. */
export interface ErrorMessage {
  code: string;
  message: string;
  matchId?: string | null;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse extends ErrorMessage {
  status: number;
  fieldErrors?: FieldError[];
}
