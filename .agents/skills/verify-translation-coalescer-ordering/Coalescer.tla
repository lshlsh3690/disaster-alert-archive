---- MODULE Coalescer ----
EXTENDS Integers, TLC

CONSTANT BuggyOrder

(* --algorithm Coalescer
variables
  mapPresent = FALSE,
  futureDone = FALSE,
  started = FALSE,
  invCount = 0;

process Thread \in {1, 2}
variables
  isExecutor = FALSE;
begin
Register:
  if ~mapPresent then
    mapPresent := TRUE;
    futureDone := FALSE;
    started := TRUE;
    isExecutor := TRUE;
  else
    isExecutor := FALSE;
  end if;
Branch:
  if isExecutor then
    goto Work;
  else
    goto Join;
  end if;
Work:
  invCount := invCount + 1;
  if BuggyOrder then
    goto RemoveFirst;
  else
    goto CompleteFirst;
  end if;
RemoveFirst:
  mapPresent := FALSE;
  goto CompleteAfterRemove;
CompleteAfterRemove:
  futureDone := TRUE;
  goto Finish;
CompleteFirst:
  futureDone := TRUE;
  goto RemoveAfterComplete;
RemoveAfterComplete:
  mapPresent := FALSE;
  goto Finish;
Join:
  await futureDone;
  goto Finish;
Finish:
  skip;
end process;

end algorithm; *)
\* BEGIN TRANSLATION (chksum(pcal) = "a99b86d6" /\ chksum(tla) = "f577ab41")
VARIABLES mapPresent, futureDone, started, invCount, pc, isExecutor

vars == << mapPresent, futureDone, started, invCount, pc, isExecutor >>

ProcSet == ({1, 2})

Init == (* Global variables *)
        /\ mapPresent = FALSE
        /\ futureDone = FALSE
        /\ started = FALSE
        /\ invCount = 0
        (* Process Thread *)
        /\ isExecutor = [self \in {1, 2} |-> FALSE]
        /\ pc = [self \in ProcSet |-> "Register"]

Register(self) == /\ pc[self] = "Register"
                  /\ IF ~mapPresent
                        THEN /\ mapPresent' = TRUE
                             /\ futureDone' = FALSE
                             /\ started' = TRUE
                             /\ isExecutor' = [isExecutor EXCEPT ![self] = TRUE]
                        ELSE /\ isExecutor' = [isExecutor EXCEPT ![self] = FALSE]
                             /\ UNCHANGED << mapPresent, futureDone, started >>
                  /\ pc' = [pc EXCEPT ![self] = "Branch"]
                  /\ UNCHANGED invCount

Branch(self) == /\ pc[self] = "Branch"
                /\ IF isExecutor[self]
                      THEN /\ pc' = [pc EXCEPT ![self] = "Work"]
                      ELSE /\ pc' = [pc EXCEPT ![self] = "Join"]
                /\ UNCHANGED << mapPresent, futureDone, started, invCount, 
                                isExecutor >>

Work(self) == /\ pc[self] = "Work"
              /\ invCount' = invCount + 1
              /\ IF BuggyOrder
                    THEN /\ pc' = [pc EXCEPT ![self] = "RemoveFirst"]
                    ELSE /\ pc' = [pc EXCEPT ![self] = "CompleteFirst"]
              /\ UNCHANGED << mapPresent, futureDone, started, isExecutor >>

RemoveFirst(self) == /\ pc[self] = "RemoveFirst"
                     /\ mapPresent' = FALSE
                     /\ pc' = [pc EXCEPT ![self] = "CompleteAfterRemove"]
                     /\ UNCHANGED << futureDone, started, invCount, isExecutor >>

CompleteAfterRemove(self) == /\ pc[self] = "CompleteAfterRemove"
                             /\ futureDone' = TRUE
                             /\ pc' = [pc EXCEPT ![self] = "Finish"]
                             /\ UNCHANGED << mapPresent, started, invCount, 
                                             isExecutor >>

CompleteFirst(self) == /\ pc[self] = "CompleteFirst"
                       /\ futureDone' = TRUE
                       /\ pc' = [pc EXCEPT ![self] = "RemoveAfterComplete"]
                       /\ UNCHANGED << mapPresent, started, invCount, 
                                       isExecutor >>

RemoveAfterComplete(self) == /\ pc[self] = "RemoveAfterComplete"
                             /\ mapPresent' = FALSE
                             /\ pc' = [pc EXCEPT ![self] = "Finish"]
                             /\ UNCHANGED << futureDone, started, invCount, 
                                             isExecutor >>

Join(self) == /\ pc[self] = "Join"
              /\ futureDone
              /\ pc' = [pc EXCEPT ![self] = "Finish"]
              /\ UNCHANGED << mapPresent, futureDone, started, invCount, 
                              isExecutor >>

Finish(self) == /\ pc[self] = "Finish"
                /\ TRUE
                /\ pc' = [pc EXCEPT ![self] = "Done"]
                /\ UNCHANGED << mapPresent, futureDone, started, invCount, 
                                isExecutor >>

Thread(self) == Register(self) \/ Branch(self) \/ Work(self)
                   \/ RemoveFirst(self) \/ CompleteAfterRemove(self)
                   \/ CompleteFirst(self) \/ RemoveAfterComplete(self)
                   \/ Join(self) \/ Finish(self)

(* Allow infinite stuttering to prevent deadlock on termination. *)
Terminating == /\ \A self \in ProcSet: pc[self] = "Done"
               /\ UNCHANGED vars

Next == (\E self \in {1, 2}: Thread(self))
           \/ Terminating

Spec == Init /\ [][Next]_vars

Termination == <>(\A self \in ProcSet: pc[self] = "Done")

\* END TRANSLATION

\* Safety property: if an item has left the map, the future backing that
\* key must already be done. This is the promise TranslationRequestCoalescer
\* makes to concurrent callers: nobody can observe a removed-but-unresolved
\* entry, because every caller either becomes the executor (and leaves the
\* map present until the future is complete) or awaits futureDone directly.
OrderInvariant == (started /\ ~mapPresent) => futureDone
====
