"use client";

import { Component, type ReactNode } from "react";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Catches render-time errors anywhere in the app and renders them through
 * the same shared ErrorDisplay every backend-driven error uses, per
 * architecture.md §Error mapping — never a bespoke crash screen.
 */
export class PageErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <ErrorDisplay
          messages={[
            {
              kind: "fullpage",
              text: "Something went wrong loading this page. Please retry.",
            },
          ]}
        />
      );
    }
    return this.props.children;
  }
}
