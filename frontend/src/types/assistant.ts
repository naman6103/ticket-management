export interface AskRequest {
  question: string;
}

export interface AskAnswer {
  answer: string;
  ticketIds: string[];
  noRelevantTicketsFound: boolean;
}
