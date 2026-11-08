((root) => {
  "use strict";

  class ApiError extends Error {
    constructor(message, status = 0) {
      super(message);
      this.name = "ApiError";
      this.status = status;
    }
  }

  const createApi = (fetchImpl = root.fetch.bind(root), baseUrl = "") => {
    const request = async (path, options = {}) => {
      let response;
      try {
        response = await fetchImpl(`${baseUrl}${path}`, {
          ...options,
          headers: {
            Accept: "application/json",
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...options.headers,
          },
        });
      } catch (error) {
        throw new ApiError(
          "Não foi possível conectar à API. Verifique se o servidor está em execução e tente novamente.",
        );
      }

      const responseText = await response.text();
      let data = null;
      if (responseText) {
        try {
          data = JSON.parse(responseText);
        } catch (error) {
          if (response.ok) {
            throw new ApiError("A API retornou uma resposta inválida.", response.status);
          }
        }
      }

      if (!response.ok) {
        throw new ApiError(
          data?.mensagem || `A API respondeu com erro HTTP ${response.status}.`,
          response.status,
        );
      }

      if (responseText && data === null) {
        throw new ApiError("A API retornou uma resposta inválida.", response.status);
      }
      return data;
    };

    return Object.freeze({
      ApiError,
      createApi,
      listRooms: () => request("/api/salas"),
      listReservations: (status) => {
        if (!status) {
          throw new ApiError("Informe o estado para listar reservas.");
        }
        return request(`/api/reservas?status=${encodeURIComponent(status)}`);
      },
      listApplicantReservations: (applicantId) =>
        request(`/api/reservas/solicitante/${encodeURIComponent(applicantId)}`),
      createReservation: (payload) =>
        request("/api/reservas", {
          method: "POST",
          body: JSON.stringify(payload),
        }),
      changeSchedule: (reservationId, payload) =>
        request(`/api/reservas/${encodeURIComponent(reservationId)}/horario`, {
          method: "PUT",
          body: JSON.stringify(payload),
        }),
      cancelReservation: (reservationId, usuarioId) =>
        request(`/api/reservas/${encodeURIComponent(reservationId)}/cancelamento`, {
          method: "POST",
          body: JSON.stringify({ usuarioId }),
        }),
      decideReservation: (reservationId, usuarioId, tipoAprovador, decision) =>
        request(
          `/api/reservas/${encodeURIComponent(reservationId)}/${decision}`,
          {
            method: "POST",
            body: JSON.stringify({ usuarioId, tipoAprovador }),
          },
        ),
    });
  };

  const api = createApi();

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  } else {
    root.ReservationApi = api;
  }
})(typeof globalThis === "undefined" ? this : globalThis);
