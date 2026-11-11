/**
 * Native steering belongs to the running transport/session, not the model name.
 * A future stateful transport may implement this contract after establishing
 * a remote session through the user's configured base URL. It must reconcile
 * accepted messages into the local transcript before acknowledging acceptance.
 * Do not pass a local Harness session ID to a cloud session API.
 */
export interface NativeSteering {
  steer(message: unknown): Promise<"accepted" | "unsupported">;
}
export async function deliverGuidance(message: unknown, local: (message: any) => void,
  native?: NativeSteering): Promise<"native" | "local"> {
  if(native) {
    try {
      if(await native.steer(message)==="accepted")return "native";
    } catch {
      // Relay rejection or transport failure: preserve the user's guidance in
      // the existing queue. Native implementations must use the message's
      // stable steerId to reconcile uncertain acknowledgements before retrying.
    }
  }
  local(message);
  return "local";
}
