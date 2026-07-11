"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Appointment } from "@/lib/types";

const STATUS_LABEL: Record<Appointment["status"], string> = {
  SCHEDULED: "Agendado",
  DONE: "Concluído",
  CANCELLED: "Cancelado",
};

export default function AgendaPage() {
  const queryClient = useQueryClient();
  const { data: appointments } = useQuery({ queryKey: ["appointments"], queryFn: api.appointments });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["appointments"] });
  const doneMutation = useMutation({ mutationFn: api.appointmentDone, onSuccess: invalidate });
  const cancelMutation = useMutation({ mutationFn: api.appointmentCancel, onSuccess: invalidate });

  const byDay = (appointments ?? []).reduce<Record<string, Appointment[]>>((groups, appointment) => {
    const day = new Date(appointment.scheduledAt).toLocaleDateString("pt-BR", {
      weekday: "long",
      day: "2-digit",
      month: "long",
    });
    return { ...groups, [day]: [...(groups[day] ?? []), appointment] };
  }, {});

  const downloadIcs = async (appointment: Appointment) => {
    const blob = await api.downloadAppointmentIcs(appointment.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${appointment.title.replaceAll(" ", "-").toLowerCase()}.ics`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="mx-auto max-w-3xl px-6 py-8">
      <h1 className="text-lg font-semibold">Agenda</h1>
      <p className="mt-1 text-sm text-ink-soft">
        Reuniões e visitas marcadas a partir das conversas. Baixe o .ics para importar em qualquer
        calendário ou encaminhar ao cliente.
      </p>

      {Object.entries(byDay).map(([day, dayAppointments]) => (
        <section key={day} className="mt-6">
          <h2 className="text-xs font-semibold tracking-wide text-ink-soft uppercase">{day}</h2>
          <ul className="mt-2 flex flex-col gap-2">
            {dayAppointments.map((appointment) => (
              <li
                key={appointment.id}
                className={`flex items-center gap-4 rounded-lg border border-line bg-paper-raised px-4 py-3 ${
                  appointment.status !== "SCHEDULED" ? "opacity-60" : ""
                }`}
              >
                <time className="w-14 shrink-0 text-center font-mono text-sm font-semibold">
                  {new Date(appointment.scheduledAt).toLocaleTimeString("pt-BR", {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </time>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{appointment.title}</p>
                  <p className="truncate text-xs text-ink-soft">
                    {appointment.contactName} · {appointment.durationMinutes} min
                    {appointment.notes ? ` · ${appointment.notes}` : ""}
                  </p>
                </div>
                <span
                  className={`shrink-0 text-xs font-medium ${
                    appointment.status === "SCHEDULED"
                      ? "text-st-open"
                      : appointment.status === "DONE"
                        ? "text-st-resolved"
                        : "text-ink-soft line-through"
                  }`}
                >
                  {STATUS_LABEL[appointment.status]}
                </span>
                <div className="flex shrink-0 items-center gap-1.5">
                  <button
                    onClick={() => downloadIcs(appointment)}
                    title="Baixar convite .ics"
                    className="rounded-md border border-line px-2 py-1 text-xs transition-colors hover:border-accent hover:text-accent-ink"
                  >
                    .ics
                  </button>
                  {appointment.status === "SCHEDULED" && (
                    <>
                      <button
                        onClick={() => doneMutation.mutate(appointment.id)}
                        className="rounded-md bg-st-resolved-soft px-2 py-1 text-xs font-medium text-st-resolved transition-opacity hover:opacity-80"
                      >
                        Concluir
                      </button>
                      <button
                        onClick={() => cancelMutation.mutate(appointment.id)}
                        className="rounded-md border border-line px-2 py-1 text-xs text-ink-soft transition-colors hover:border-danger hover:text-danger"
                      >
                        Cancelar
                      </button>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
      {appointments?.length === 0 && (
        <p className="mt-8 rounded-lg border border-dashed border-line-strong px-4 py-8 text-center text-sm text-ink-soft">
          Nada agendado — marque uma visita ou reunião pelo botão “Agendar” dentro de uma conversa.
        </p>
      )}
    </div>
  );
}
