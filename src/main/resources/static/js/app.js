(() => {
  "use strict";

  const {
    ROOM_STATUS,
    RESERVATION_STATUS,
    ROOM_STATUSES,
    RESERVATION_STATUSES,
  } = window.ReservationRules;

  const identityStorageKey = "salas-senac-api-identities";
  const defaultIdentity = {
    solicitanteId: "",
    tipoSolicitante: "PROFESSOR",
    responsavelId: "",
    administradorId: "",
  };
  const normalizeId = (value) => {
    const id = String(value ?? "");
    return /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id)) ? id : "";
  };
  const loadIdentity = () => {
    try {
      const stored = window.localStorage.getItem(identityStorageKey);
      if (!stored) {
        return { ...defaultIdentity };
      }
      const saved = JSON.parse(stored);
      return {
        solicitanteId: normalizeId(saved.solicitanteId),
        tipoSolicitante: ["PROFESSOR", "COORDENADOR"].includes(
          saved.tipoSolicitante,
        )
          ? saved.tipoSolicitante
          : defaultIdentity.tipoSolicitante,
        responsavelId: normalizeId(saved.responsavelId),
        administradorId: normalizeId(saved.administradorId),
      };
    } catch (error) {
      console.error("Não foi possível carregar os IDs salvos para a API.", error);
      return { ...defaultIdentity };
    }
  };

  const dayOffset = (offset) => {
    const date = new Date();
    date.setDate(date.getDate() + offset);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  };

  const state = {
    profile: null,
    currentUser: null,
    applicantView: "rooms",
    roomFilter: "TODAS",
    searchTerm: "",
    identity: loadIdentity(),
    rooms: [],
    reservations: [],
    pendingReservations: [],
    loading: true,
    apiError: "",
  };

  const content = document.getElementById("app-content");
  const loginScreen = document.getElementById("login-screen");
  const application = document.getElementById("application");
  const loginForm = document.getElementById("login-form");
  const loginError = document.getElementById("login-error");
  const accountName = document.getElementById("account-name");
  const profileLabel = document.getElementById("active-profile-label");
  const pendingCount = document.getElementById("pending-count");
  const todayLabel = document.getElementById("today-label");
  const dialog = document.getElementById("reservation-dialog");
  const identityDialog = document.getElementById("identity-dialog");
  const reservationForm = document.getElementById("reservation-form");
  const identityForm = document.getElementById("identity-form");
  const formError = document.getElementById("form-error");
  const connectionStatus = document.getElementById("connection-status");
  const profileNames = {
    solicitante: "Solicitante",
    responsavel: "Responsável",
    administrador: "Administrador",
  };

  const escapeHtml = (value) =>
    String(value).replace(/[&<>"']/g, (character) => {
      const entities = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      };
      return entities[character];
    });

  const getRoom = (roomId) =>
    state.rooms.find((room) => room.id === Number(roomId));

  const getReservation = (reservationId) =>
    state.reservations.find(
      (reservation) => String(reservation.id) === String(reservationId),
    ) ||
    state.pendingReservations.find(
      (reservation) => String(reservation.id) === String(reservationId),
    );

  const roomStatusClass = (status) => {
    const classes = {
      DISPONIVEL: "status-disponivel",
      RESERVADA: "status-reservada",
      EM_MANUTENCAO: "status-em-manutencao",
    };
    return classes[status] || "";
  };

  const reservationStatusClass = (status) => {
    const classes = {
      SOLICITADA: "status-solicitada",
      APROVADA: "status-aprovada",
      EM_USO: "status-em-uso",
      CONCLUIDA: "status-concluida",
      REJEITADA: "status-rejeitada",
      CANCELADA: "status-cancelada",
      NAO_COMPARECEU: "status-nao-compareceu",
    };
    return classes[status] || "";
  };

  const getRoomStatusDescription = (status) => {
    const descriptions = {
      DISPONIVEL: "Apta para nova solicitação",
      RESERVADA: "Já possui uma reserva",
      EM_MANUTENCAO: "Indisponível para reserva",
    };
    return descriptions[status] || "Estado não reconhecido";
  };

  const formatDate = (dateTime) => {
    const date = new Date(`${dateTime.slice(0, 10)}T12:00:00`);
    return new Intl.DateTimeFormat("pt-BR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }).format(date);
  };

  const formatTime = (dateTime) => dateTime.slice(11, 16);

  const showToast = (message, isError = false) => {
    const region = document.getElementById("toast-region");
    const toast = document.createElement("div");
    toast.className = `toast${isError ? " is-error" : ""}`;
    toast.setAttribute("role", isError ? "alert" : "status");
    toast.textContent = message;
    region.append(toast);
    window.setTimeout(() => toast.remove(), 4200);
  };

  const renderStats = () => {
    const available = state.rooms.filter(
      (room) => room.statusSala === ROOM_STATUS.AVAILABLE,
    ).length;
    const reserved = state.rooms.filter(
      (room) => room.statusSala === ROOM_STATUS.RESERVED,
    ).length;
    const pending = state.pendingReservations.length;
    return `
      <section class="stats-grid" aria-label="Resumo de salas e solicitações">
        <article class="stat-card">
          <span class="stat-label">Salas cadastradas</span>
          <strong class="stat-value">${state.rooms.length}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">Disponíveis agora</span>
          <strong class="stat-value">${available}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">Aguardando análise</span>
          <strong class="stat-value">${pending}</strong>
        </article>
      </section>
      <span class="sr-only">${reserved} salas reservadas.</span>
    `;
  };

  const renderHeading = (eyebrow, title, description, action = "") => `
    <section class="page-heading">
      <div>
        <span class="eyebrow">${eyebrow}</span>
        <h1>${title}</h1>
        <p>${description}</p>
      </div>
      ${action}
    </section>
  `;

  const renderRoomCard = (room) => {
    const available = room.statusSala === ROOM_STATUS.AVAILABLE;
    const unavailableClass = available ? "" : " is-unavailable";
    const safeStatus = ROOM_STATUSES.includes(room.statusSala)
      ? room.statusSala
      : ROOM_STATUS.MAINTENANCE;
    const reserveButton = available
      ? `<button class="button button-orange button-small" type="button" data-action="new-reservation" data-room-id="${room.id}">Reservar sala</button>`
      : `<button class="button button-quiet button-small" type="button" disabled aria-label="Sala ${room.id} indisponível">${safeStatus === ROOM_STATUS.MAINTENANCE ? "Em manutenção" : "Indisponível"}</button>`;
    return `
      <article class="room-card${unavailableClass}" data-status="${safeStatus}">
        <div class="room-card-top">
          <div>
            <div class="room-title-row">
              <h3 class="room-title">Sala ${room.id}</h3>
              <span class="room-id">#${room.id}</span>
            </div>
            <p class="room-capacity">Capacidade para ${room.capacidade} pessoas</p>
          </div>
          <span class="status-badge ${roomStatusClass(safeStatus)}" aria-label="Estado da sala: ${safeStatus}">${safeStatus}</span>
        </div>
        <div class="room-card-footer">
          <span class="availability-copy">${getRoomStatusDescription(safeStatus)}</span>
          ${reserveButton}
        </div>
      </article>
    `;
  };

  const getVisibleRooms = () => {
    const query = state.searchTerm.trim().toLocaleLowerCase("pt-BR");
    return state.rooms.filter((room) => {
      const matchesFilter =
        state.roomFilter === "TODAS" || room.statusSala === state.roomFilter;
      const matchesSearch =
        !query ||
        String(room.id).includes(query) ||
        String(room.capacidade).includes(query) ||
        room.statusSala.toLocaleLowerCase("pt-BR").includes(query);
      return matchesFilter && matchesSearch;
    });
  };

  const renderRoomGrid = () => {
    const visibleRooms = getVisibleRooms();
    const grid = document.getElementById("room-grid");
    if (!grid) {
      return;
    }
    grid.innerHTML = visibleRooms.length
      ? visibleRooms.map(renderRoomCard).join("")
      : `<div class="empty-state">Nenhuma sala encontrada com esses filtros.</div>`;
  };

  const renderConnectionStatus = () => {
    if (state.loading) {
      connectionStatus.innerHTML =
        '<div class="info-banner" role="status">Sincronizando dados com o servidor…</div>';
    } else if (state.apiError) {
      connectionStatus.innerHTML = `
        <div class="info-banner is-error" role="alert">
          <span>${escapeHtml(state.apiError)}</span>
          <button class="button button-quiet button-small" type="button" id="retry-api">Tentar novamente</button>
        </div>
      `;
    } else {
      connectionStatus.innerHTML = "";
    }
  };

  const renderIdentitySummary = () => {
    const identity = state.identity;
    const label =
      state.profile === "solicitante"
        ? identity.solicitanteId
          ? `Solicitante #${identity.solicitanteId}`
          : "Solicitante não configurado"
        : state.profile === "responsavel"
          ? identity.responsavelId
            ? `Responsável #${identity.responsavelId}`
            : "Responsável não configurado"
          : identity.administradorId
            ? `Administrador #${identity.administradorId}`
            : "Administrador não configurado";
    document.getElementById("identity-summary").textContent = label;
  };

  const renderMyReservations = () => {
    if (!state.identity.solicitanteId) {
      return `<div class="empty-state">Configure o ID do solicitante para consultar suas reservas.</div>`;
    }
    if (state.loading) {
      return `<div class="empty-state">Carregando suas reservas…</div>`;
    }
    if (state.apiError) {
      return `<div class="empty-state">Não foi possível carregar suas reservas do servidor.</div>`;
    }
    const ownReservations = state.reservations;
    if (ownReservations.length === 0) {
      return `<div class="empty-state">Você ainda não possui solicitações. Consulte as salas disponíveis para começar.</div>`;
    }
    return `
      <div class="panel">
        <div class="panel-heading">
          <div>
            <h2>Minhas solicitações</h2>
            <p>Acompanhe as datas e o estado de cada reserva.</p>
          </div>
        </div>
        <div class="reservation-list">
          ${ownReservations
            .map((reservation) => {
              const room = getRoom(reservation.salaId);
              const roomLabel = room ? `Sala ${room.id}` : `Sala ${reservation.salaId}`;
              const canEdit = reservation.status === RESERVATION_STATUS.REQUESTED;
              const canCancel = [
                RESERVATION_STATUS.REQUESTED,
                RESERVATION_STATUS.APPROVED,
              ].includes(reservation.status);
              const statusClass = RESERVATION_STATUSES.includes(reservation.status)
                ? reservationStatusClass(reservation.status)
                : "";
              return `
                <article class="reservation-row">
                  <div class="reservation-primary">
                    <strong>${roomLabel} <span class="room-id">· ${reservation.id}</span></strong>
                    <span>Solicitante: Você</span>
                  </div>
                  <div class="reservation-detail">
                    <strong>${formatDate(reservation.inicio)}</strong>
                    <span>${formatTime(reservation.inicio)} – ${formatTime(reservation.fim)}</span>
                  </div>
                  <div class="reservation-detail">
                    <span>Estado da reserva</span>
                    <span class="status-badge ${statusClass}" aria-label="Estado da reserva: ${reservation.status}">${reservation.status}</span>
                  </div>
                  <div class="reservation-actions">
                    ${canEdit ? `<button class="button button-quiet button-small" type="button" data-action="edit-reservation" data-reservation-id="${reservation.id}">Alterar data</button>` : ""}
                    ${canCancel ? `<button class="button button-danger button-small" type="button" data-action="cancel-reservation" data-reservation-id="${reservation.id}">Cancelar</button>` : ""}
                  </div>
                </article>
              `;
            })
            .join("")}
        </div>
      </div>
    `;
  };

  const renderApplicant = () => {
    const action = `<button class="button button-primary" type="button" data-action="show-rooms">Ver salas disponíveis <span aria-hidden="true">→</span></button>`;
    const heading = renderHeading(
      "ESPAÇOS PARA SUA PRÓXIMA AULA",
      "Encontre sua sala",
      "Consulte a disponibilidade e acompanhe suas solicitações de reserva.",
      action,
    );
    const contentSection =
      state.loading && state.applicantView === "rooms"
        ? `<div class="empty-state">Carregando salas do servidor…</div>`
        : state.apiError && state.applicantView === "rooms"
          ? `<div class="empty-state">Não foi possível carregar as salas do servidor.</div>`
        : state.applicantView === "rooms"
          ? `
            <div class="toolbar">
              <div class="segmented-control" role="group" aria-label="Filtrar salas por estado">
                ${["TODAS", ...ROOM_STATUSES]
                  .map(
                    (status) => `
                      <button class="segment-button${state.roomFilter === status ? " is-active" : ""}" type="button" data-room-filter="${status}" aria-pressed="${state.roomFilter === status}">
                        ${status === "TODAS" ? "Todas" : status}
                      </button>
                    `,
                  )
                  .join("")}
              </div>
              <label>
                <span class="sr-only">Buscar por sala, capacidade ou estado</span>
                <input class="search-field" id="room-search" type="search" placeholder="Buscar sala ou capacidade" value="${escapeHtml(state.searchTerm)}">
              </label>
            </div>
            <div class="room-grid" id="room-grid"></div>
          `
          : renderMyReservations();
    content.innerHTML = `
      ${heading}
      ${renderStats()}
      <div class="subnav" role="tablist" aria-label="Conteúdo do solicitante">
        <button class="subnav-button${state.applicantView === "rooms" ? " is-active" : ""}" type="button" role="tab" aria-selected="${state.applicantView === "rooms"}" data-applicant-view="rooms">Disponibilidade</button>
        <button class="subnav-button${state.applicantView === "reservations" ? " is-active" : ""}" type="button" role="tab" aria-selected="${state.applicantView === "reservations"}" data-applicant-view="reservations">Minhas solicitações</button>
      </div>
      ${contentSection}
    `;
    if (state.applicantView === "rooms") {
      renderRoomGrid();
    }
  };

  const renderPendingRequests = () => {
    const pending = state.pendingReservations;
    const requestContent = state.loading
      ? `<div class="empty-state">Carregando solicitações do servidor…</div>`
      : state.apiError
        ? `<div class="empty-state">Não foi possível carregar as solicitações do servidor.</div>`
      : pending.length
      ? pending
          .map((reservation) => {
            const room = getRoom(reservation.salaId);
            const roomName = room ? `Sala ${room.id}` : `Sala ${reservation.salaId}`;
            const approvalBlocked = room?.statusSala === ROOM_STATUS.MAINTENANCE;
            return `
              <article class="request-card">
                <div class="request-main">
                  <div class="request-heading">
                    <h3>Solicitante #${reservation.solicitanteId}</h3>
                    <span class="status-badge ${reservationStatusClass(reservation.status)}">${reservation.status}</span>
                  </div>
                  <div class="request-meta">
                    <span><strong>${roomName}</strong></span>
                    <span>${formatDate(reservation.inicio)}</span>
                    <span>${formatTime(reservation.inicio)} – ${formatTime(reservation.fim)}</span>
                    <span>Solicitação <strong>${reservation.id}</strong></span>
                    ${approvalBlocked ? "<span>Sala em manutenção: aprovação indisponível.</span>" : ""}
                  </div>
                </div>
                <div class="request-actions">
                  <button class="button button-quiet button-small" type="button" data-action="reject-reservation" data-reservation-id="${reservation.id}">Rejeitar</button>
                  <button class="button button-primary button-small" type="button" data-action="approve-reservation" data-reservation-id="${reservation.id}"${approvalBlocked ? " disabled" : ""}>Aprovar</button>
                </div>
              </article>
            `;
          })
          .join("")
      : `<div class="empty-state">Não há solicitações pendentes para analisar.</div>`;

    return `
      <section class="panel" aria-labelledby="requests-title">
        <div class="panel-heading">
          <div>
            <h2 id="requests-title">Fila de análise</h2>
            <p>${pending.length} ${pending.length === 1 ? "solicitação aguarda" : "solicitações aguardam"} uma decisão.</p>
          </div>
        </div>
        <div class="reservation-list">${requestContent}</div>
      </section>
    `;
  };

  const renderResponsible = () => {
    content.innerHTML = `
      ${renderHeading(
        "ANÁLISE DE RESERVAS",
        "Solicitações pendentes",
        "Revise os dados de cada pedido e registre sua decisão.",
      )}
      ${renderStats()}
      <div class="info-banner">
        <span class="info-icon" aria-hidden="true">i</span>
        <span>Apenas solicitações no estado <strong>SOLICITADA</strong> podem ser aprovadas ou rejeitadas.</span>
      </div>
      ${renderPendingRequests()}
    `;
  };

  const renderAdministrator = () => {
    const maintenanceCount = state.rooms.filter(
      (room) => room.statusSala === ROOM_STATUS.MAINTENANCE,
    ).length;
    content.innerHTML = `
      ${renderHeading(
        "CONTROLE DE RECURSOS",
        "Consultar salas",
        "Consulte os dados das salas cadastradas no servidor.",
      )}
      ${renderStats()}
      <div class="info-banner">
        <span class="info-icon" aria-hidden="true">i</span>
        <span>A API ainda não oferece operações administrativas para alterar cadastro, capacidade ou estado das salas. ${maintenanceCount} ${maintenanceCount === 1 ? "sala está" : "salas estão"} em manutenção.</span>
      </div>
      <section aria-labelledby="admin-rooms-title">
        <div class="section-heading">
          <div>
            <h2 id="admin-rooms-title">Salas cadastradas</h2>
            <p>Dados consultados diretamente do servidor.</p>
          </div>
        </div>
        ${
          state.loading
            ? '<div class="empty-state">Carregando salas do servidor…</div>'
            : state.apiError
              ? '<div class="empty-state">Não foi possível carregar as salas do servidor.</div>'
            : `<div class="room-grid">${
                state.rooms.length
                  ? state.rooms
                      .map((room) => {
                        const safeStatus = ROOM_STATUSES.includes(room.statusSala)
                          ? room.statusSala
                          : ROOM_STATUS.MAINTENANCE;
                        return `
                          <article class="room-card${safeStatus === ROOM_STATUS.AVAILABLE ? "" : " is-unavailable"}">
                            <div class="room-card-top">
                              <div>
                                <div class="room-title-row"><h3 class="room-title">Sala ${room.id}</h3><span class="room-id">#${room.id}</span></div>
                                <p class="room-capacity">Capacidade para ${room.capacidade} pessoas</p>
                              </div>
                              <span class="status-badge ${roomStatusClass(safeStatus)}">${safeStatus}</span>
                            </div>
                            <div class="room-card-footer"><span class="availability-copy">${getRoomStatusDescription(safeStatus)}</span></div>
                          </article>
                        `;
                      })
                      .join("")
                  : '<div class="empty-state">Nenhuma sala cadastrada.</div>'
              }</div>`
        }
      </section>
      <section aria-labelledby="admin-requests-title">
        <div class="section-heading">
          <div>
            <h2 id="admin-requests-title">Solicitações pendentes</h2>
            <p>Administradores também podem registrar decisões pela API.</p>
          </div>
        </div>
        ${renderPendingRequests()}
      </section>
    `;
  };

  const render = () => {
    if (!state.currentUser) {
      return;
    }
    profileLabel.textContent = profileNames[state.profile];
    const pending = state.pendingReservations.length;
    pendingCount.textContent = String(pending);
    pendingCount.classList.toggle("has-count", pending > 0);
    renderConnectionStatus();
    renderIdentitySummary();
    document.querySelectorAll("[data-profile]").forEach((button) => {
      const allowed = allowedProfiles[state.currentUser.role].includes(
        button.dataset.profile,
      );
      button.hidden = !allowed;
      const active = button.dataset.profile === state.profile;
      button.classList.toggle("is-active", active);
      if (active && allowed) {
        button.setAttribute("aria-current", "page");
      } else {
        button.removeAttribute("aria-current");
      }
    });

    if (state.profile === "responsavel") {
      renderResponsible();
    } else if (state.profile === "administrador") {
      renderAdministrator();
    } else {
      renderApplicant();
    }
  };

  const setProfile = (profile) => {
    if (
      !state.currentUser ||
      !allowedProfiles[state.currentUser.role].includes(profile)
    ) {
      return;
    }
    state.profile = profile;
    state.applicantView = profile === "solicitante" ? state.applicantView : "rooms";
    render();
    void refreshData();
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  loginForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const formData = new FormData(loginForm);
    const email = String(formData.get("email")).trim().toLocaleLowerCase("pt-BR");
    const password = String(formData.get("password"));
    const user = mockUsers.find(
      (account) => account.email === email && account.password === password,
    );

    if (!user) {
      loginError.textContent = "E-mail ou senha inválidos. Confira os dados e tente novamente.";
      return;
    }

    state.currentUser = user;
    state.profile = user.role === "administrador" ? "administrador" : user.role;
    loginError.textContent = "";
    loginScreen.hidden = true;
    application.hidden = false;
    render();
  });

  loginForm.addEventListener("input", () => {
    loginError.textContent = "";
  });

  document.getElementById("logout-button").addEventListener("click", () => {
    state.currentUser = null;
    state.profile = null;
    application.hidden = true;
    loginScreen.hidden = false;
    loginForm.reset();
    loginError.textContent = "";
    document.getElementById("login-email").focus();
  });

  const setDialogDateDefaults = () => {
    document.getElementById("start-date").value = dayOffset(1);
    document.getElementById("start-time").value = "09:00";
    document.getElementById("end-date").value = dayOffset(1);
    document.getElementById("end-time").value = "10:00";
  };

  let refreshSequence = 0;
  const refreshData = async () => {
    const sequence = ++refreshSequence;
    state.loading = true;
    state.apiError = "";
    render();
    try {
      const [rooms, pendingReservations] = await Promise.all([
        window.ReservationApi.listRooms(),
        window.ReservationApi.listReservations(RESERVATION_STATUS.REQUESTED),
      ]);
      if (sequence !== refreshSequence) {
        return;
      }
      if (!Array.isArray(rooms) || !Array.isArray(pendingReservations)) {
        throw new window.ReservationApi.ApiError(
          "A API retornou dados em um formato inesperado.",
        );
      }

      state.rooms = rooms;
      state.pendingReservations = pendingReservations;
      if (
        state.profile === "solicitante" &&
        state.applicantView === "reservations" &&
        state.identity.solicitanteId
      ) {
        const reservations =
          await window.ReservationApi.listApplicantReservations(
            state.identity.solicitanteId,
          );
        if (sequence !== refreshSequence) {
          return;
        }
        if (!Array.isArray(reservations)) {
          throw new window.ReservationApi.ApiError(
            "A API retornou uma lista de reservas inválida.",
          );
        }
        state.reservations = reservations;
      } else {
        state.reservations = [];
      }
    } catch (error) {
      if (sequence === refreshSequence) {
        console.error("Falha ao sincronizar com a API de reservas.", error);
        state.apiError =
          error instanceof window.ReservationApi.ApiError
            ? error.message
            : "Ocorreu um erro inesperado ao consultar o servidor.";
      }
    } finally {
      if (sequence === refreshSequence) {
        state.loading = false;
        render();
      }
    }
  };

  const showApiError = (error) => {
    console.error("Falha em operação da API de reservas.", error);
    showToast(
      error instanceof window.ReservationApi.ApiError
        ? error.message
        : "Ocorreu um erro inesperado ao executar a operação.",
      true,
    );
  };

  const openIdentityDialog = () => {
    document.getElementById("identity-applicant-id").value =
      state.identity.solicitanteId;
    document.getElementById("identity-applicant-type").value =
      state.identity.tipoSolicitante;
    document.getElementById("identity-responsible-id").value =
      state.identity.responsavelId;
    document.getElementById("identity-administrator-id").value =
      state.identity.administradorId;
    identityDialog.showModal();
  };

  const saveIdentity = (event) => {
    event.preventDefault();
    const idFields = [
      "identity-applicant-id",
      "identity-responsible-id",
      "identity-administrator-id",
    ];
    const hasInvalidId = idFields.some((id) => {
      const value = document.getElementById(id).value.trim();
      return value !== "" && !normalizeId(value);
    });
    if (hasInvalidId) {
      showToast("Informe IDs inteiros positivos e dentro do limite seguro.", true);
      return;
    }
    const readId = (id) => {
      const value = document.getElementById(id).value.trim();
      return value ? normalizeId(value) : "";
    };
    const nextIdentity = {
      solicitanteId: readId("identity-applicant-id"),
      tipoSolicitante: document.getElementById("identity-applicant-type").value,
      responsavelId: readId("identity-responsible-id"),
      administradorId: readId("identity-administrator-id"),
    };
    if (
      !["PROFESSOR", "COORDENADOR"].includes(nextIdentity.tipoSolicitante)
    ) {
      showToast("Selecione um tipo de solicitante válido.", true);
      return;
    }
    try {
      window.localStorage.setItem(
        identityStorageKey,
        JSON.stringify(nextIdentity),
      );
    } catch (error) {
      console.error("Não foi possível salvar os IDs para a API.", error);
      showToast("Não foi possível salvar a configuração neste navegador.", true);
      return;
    }
    state.identity = nextIdentity;
    identityDialog.close();
    showToast("IDs da sessão atualizados.");
    void refreshData();
  };

  const openReservationDialog = (roomId, reservationId = null) => {
    const room = getRoom(roomId);
    const reservation = reservationId ? getReservation(reservationId) : null;
    if (!room) {
      showToast("Não foi possível localizar esta sala.", true);
      return;
    }
    if (!state.identity.solicitanteId) {
      showToast("Configure o ID do solicitante antes de criar ou alterar uma reserva.", true);
      openIdentityDialog();
      return;
    }
    if (!reservation && !window.ReservationRules.canRequestRoom(room)) {
      showToast("Esta sala não está disponível para novas solicitações.", true);
      return;
    }

    document.getElementById("reservation-id").value = reservationId || "";
    document.getElementById("reservation-room-id").value = String(room.id);
    formError.textContent = "";
    setDialogDateDefaults();

    const isEditing = Boolean(reservation);
    document.getElementById("dialog-title").textContent = isEditing
      ? "Alterar data da reserva"
      : "Nova solicitação";
    document.getElementById("dialog-description").textContent = isEditing
      ? `Atualize o intervalo da reserva da Sala ${room.id}.`
      : `Informe o período desejado para a Sala ${room.id}.`;
    document.getElementById("reservation-submit").textContent = isEditing
      ? "Salvar nova data"
      : "Enviar solicitação";

    if (reservation) {
      if (reservation.status !== RESERVATION_STATUS.REQUESTED) {
        showToast("Somente reservas solicitadas podem ter o horário alterado.", true);
        return;
      }
      document.getElementById("start-date").value = reservation.inicio.slice(0, 10);
      document.getElementById("start-time").value = formatTime(reservation.inicio);
      document.getElementById("end-date").value = reservation.fim.slice(0, 10);
      document.getElementById("end-time").value = formatTime(reservation.fim);
    }

    dialog.showModal();
  };

  const handleReservationSubmit = async (event) => {
    event.preventDefault();
    formError.textContent = "";
    const reservationId = document.getElementById("reservation-id").value;
    const roomId = Number(document.getElementById("reservation-room-id").value);
    const room = getRoom(roomId);
    const startDate = document.getElementById("start-date").value;
    const startTime = document.getElementById("start-time").value;
    const endDate = document.getElementById("end-date").value;
    const endTime = document.getElementById("end-time").value;
    const nextStart = `${startDate}T${startTime}`;
    const nextEnd = `${endDate}T${endTime}`;

    if (!room || !window.ReservationRules.isValidInterval(nextStart, nextEnd)) {
      formError.textContent = "Informe datas válidas e um término posterior ao início.";
      return;
    }
    if (!state.identity.solicitanteId) {
      formError.textContent = "Configure o ID do solicitante antes de continuar.";
      return;
    }
    if (!reservationId && !window.ReservationRules.canRequestRoom(room)) {
      formError.textContent = "Esta sala não está disponível para novas solicitações.";
      return;
    }

    const submitButton = document.getElementById("reservation-submit");
    submitButton.disabled = true;
    try {
      if (reservationId) {
        await window.ReservationApi.changeSchedule(reservationId, {
          solicitanteId: Number(state.identity.solicitanteId),
          inicio: nextStart,
          fim: nextEnd,
        });
      } else {
        await window.ReservationApi.createReservation({
          salaId: roomId,
          solicitanteId: Number(state.identity.solicitanteId),
          tipoSolicitante: state.identity.tipoSolicitante,
          inicio: nextStart,
          fim: nextEnd,
        });
      }
      dialog.close();
      if (!reservationId) {
        state.applicantView = "reservations";
      }
      showToast(
        reservationId
          ? "Data e horário da reserva atualizados."
          : `Solicitação enviada para análise: Sala ${room.id}.`,
      );
      await refreshData();
    } catch (error) {
      formError.textContent =
        error instanceof window.ReservationApi.ApiError
          ? error.message
          : "Ocorreu um erro inesperado ao salvar a reserva.";
      console.error("Falha ao salvar reserva.", error);
    } finally {
      submitButton.disabled = false;
    }
  };

  const handleReservationDecision = async (reservationId, decision) => {
    const isResponsible = state.profile === "responsavel";
    const userId = isResponsible
      ? state.identity.responsavelId
      : state.identity.administradorId;
    const tipoAprovador = isResponsible ? "RESPONSAVEL" : "ADMINISTRADOR";
    if (!userId) {
      showToast(`Configure o ID de ${tipoAprovador.toLowerCase()} antes de decidir.`, true);
      openIdentityDialog();
      return;
    }
    try {
      await window.ReservationApi.decideReservation(
        reservationId,
        Number(userId),
        tipoAprovador,
        decision === RESERVATION_STATUS.APPROVED ? "aprovacao" : "rejeicao",
      );
      showToast(
        decision === RESERVATION_STATUS.APPROVED
          ? `Solicitação ${reservationId} aprovada.`
          : `Solicitação ${reservationId} rejeitada.`,
      );
      await refreshData();
    } catch (error) {
      showApiError(error);
    }
  };

  const cancelReservation = async (reservationId) => {
    const reservation = getReservation(reservationId);
    if (
      !reservation ||
      ![RESERVATION_STATUS.REQUESTED, RESERVATION_STATUS.APPROVED].includes(
        reservation.status,
      )
    ) {
      showToast("Esta reserva não pode mais ser cancelada.", true);
      return;
    }
    if (!state.identity.solicitanteId) {
      showToast("Configure o ID do solicitante antes de cancelar.", true);
      openIdentityDialog();
      return;
    }
    try {
      await window.ReservationApi.cancelReservation(
        reservationId,
        Number(state.identity.solicitanteId),
      );
      showToast(`Reserva ${reservationId} cancelada.`);
      await refreshData();
    } catch (error) {
      showApiError(error);
    }
  };

  document.querySelectorAll("[data-profile]").forEach((button) => {
    button.addEventListener("click", () => setProfile(button.dataset.profile));
  });

  content.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) {
      return;
    }
    const applicantTab = target.closest("[data-applicant-view]");
    if (applicantTab) {
      state.applicantView = applicantTab.dataset.applicantView;
      render();
      void refreshData();
      return;
    }
    const roomFilter = target.closest("[data-room-filter]");
    if (roomFilter) {
      state.roomFilter = roomFilter.dataset.roomFilter;
      render();
      return;
    }
    const actionButton = target.closest("[data-action]");
    if (!actionButton) {
      return;
    }

    const { action, roomId, reservationId } = actionButton.dataset;
    if (action === "new-reservation") {
      openReservationDialog(roomId);
    } else if (action === "edit-reservation") {
      const reservation = getReservation(reservationId);
      if (reservation) {
        openReservationDialog(reservation.salaId, reservation.id);
      }
    } else if (action === "cancel-reservation") {
      cancelReservation(reservationId);
    } else if (action === "approve-reservation") {
      handleReservationDecision(reservationId, RESERVATION_STATUS.APPROVED);
    } else if (action === "reject-reservation") {
      handleReservationDecision(reservationId, RESERVATION_STATUS.REJECTED);
    } else if (action === "show-rooms") {
      state.applicantView = "rooms";
      render();
      void refreshData();
    }
  });

  connectionStatus.addEventListener("click", (event) => {
    if (event.target instanceof Element && event.target.id === "retry-api") {
      void refreshData();
    }
  });

  content.addEventListener("input", (event) => {
    const target = event.target;
    if (target instanceof HTMLInputElement && target.id === "room-search") {
      state.searchTerm = target.value;
      renderRoomGrid();
    }
  });

  reservationForm.addEventListener("submit", handleReservationSubmit);
  identityForm.addEventListener("submit", saveIdentity);
  document
    .getElementById("configure-identity")
    .addEventListener("click", openIdentityDialog);
  dialog.querySelectorAll("[data-close-dialog]").forEach((button) => {
    button.addEventListener("click", () => dialog.close());
  });
  identityDialog.querySelectorAll("[data-close-identity]").forEach((button) => {
    button.addEventListener("click", () => identityDialog.close());
  });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) {
      dialog.close();
    }
  });
  identityDialog.addEventListener("click", (event) => {
    if (event.target === identityDialog) {
      identityDialog.close();
    }
  });

  todayLabel.textContent = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  }).format(new Date());

  render();
  void refreshData();
})();
