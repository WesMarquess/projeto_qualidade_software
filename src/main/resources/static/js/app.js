(() => {
  "use strict";

  const {
    ROOM_STATUS,
    RESERVATION_STATUS,
    ROOM_STATUSES,
    RESERVATION_STATUSES,
    ACTIVE_RESERVATION_STATUSES,
  } = window.ReservationRules;

  const mockUsers = [
    {
      name: "Camila Santos",
      email: "solicitante@senac.local",
      password: "solicitante123",
      role: "solicitante",
    },
    {
      name: "Rafael Lima",
      email: "responsavel@senac.local",
      password: "responsavel123",
      role: "responsavel",
    },
    {
      name: "Mariana Costa",
      email: "admin@senac.local",
      password: "admin123",
      role: "administrador",
    },
  ];
  const allowedProfiles = {
    solicitante: ["solicitante"],
    responsavel: ["responsavel"],
    administrador: ["administrador", "responsavel"],
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
    rooms: [
      { id: 101, capacidade: 30, statusSala: ROOM_STATUS.AVAILABLE },
      { id: 204, capacidade: 24, statusSala: ROOM_STATUS.RESERVED },
      { id: 305, capacidade: 40, statusSala: ROOM_STATUS.MAINTENANCE },
      { id: 408, capacidade: 22, statusSala: ROOM_STATUS.RESERVED },
    ],
    reservations: [
      {
        id: "RSV-204",
        salaId: 204,
        solicitante: "Você",
        inicio: `${dayOffset(1)}T09:00`,
        fim: `${dayOffset(1)}T11:00`,
        status: RESERVATION_STATUS.APPROVED,
      },
      {
        id: "RSV-408",
        salaId: 408,
        solicitante: "Marina Alves",
        inicio: `${dayOffset(2)}T13:00`,
        fim: `${dayOffset(2)}T15:00`,
        status: RESERVATION_STATUS.REQUESTED,
      },
    ],
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
  const reservationForm = document.getElementById("reservation-form");
  const formError = document.getElementById("form-error");
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
    state.reservations.find((reservation) => reservation.id === reservationId);

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

  const updateRoomAvailability = (roomId) => {
    const room = getRoom(roomId);
    if (!room) {
      return;
    }
    room.statusSala = window.ReservationRules.getRoomAvailability(
      room.statusSala,
      state.reservations,
      roomId,
    );
  };

  const renderStats = () => {
    const available = state.rooms.filter(
      (room) => room.statusSala === ROOM_STATUS.AVAILABLE,
    ).length;
    const reserved = state.rooms.filter(
      (room) => room.statusSala === ROOM_STATUS.RESERVED,
    ).length;
    const pending = state.reservations.filter(
      (reservation) => reservation.status === RESERVATION_STATUS.REQUESTED,
    ).length;
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

  const renderMyReservations = () => {
    const ownReservations = state.reservations.filter(
      (reservation) => reservation.solicitante === "Você",
    );
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
              const canManage = [
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
                    <span>Solicitante: ${escapeHtml(reservation.solicitante)}</span>
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
                    ${canManage ? `<button class="button button-quiet button-small" type="button" data-action="edit-reservation" data-reservation-id="${reservation.id}">Alterar data</button>` : ""}
                    ${canManage ? `<button class="button button-danger button-small" type="button" data-action="cancel-reservation" data-reservation-id="${reservation.id}">Cancelar</button>` : ""}
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
      state.applicantView === "rooms"
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

  const renderResponsible = () => {
    const pending = state.reservations.filter(
      (reservation) => reservation.status === RESERVATION_STATUS.REQUESTED,
    );
    const requestContent = pending.length
      ? pending
          .map((reservation) => {
            const room = getRoom(reservation.salaId);
            const roomName = room ? `Sala ${room.id}` : `Sala ${reservation.salaId}`;
            const approvalBlocked = room?.statusSala === ROOM_STATUS.MAINTENANCE;
            return `
              <article class="request-card">
                <div class="request-main">
                  <div class="request-heading">
                    <h3>${escapeHtml(reservation.solicitante)}</h3>
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

  const renderAdminRooms = () => `
    <div class="room-admin-grid">
      ${state.rooms
        .map((room) => {
          const safeStatus = ROOM_STATUSES.includes(room.statusSala)
            ? room.statusSala
            : ROOM_STATUS.MAINTENANCE;
          return `
            <form class="admin-room-row" data-admin-room="${room.id}">
              <div class="admin-room-name">
                <strong>Sala ${room.id}</strong>
                <span>Estado atual: <span class="status-badge ${roomStatusClass(safeStatus)}">${safeStatus}</span></span>
              </div>
              <div class="admin-field">
                <label for="capacity-${room.id}">Capacidade</label>
                <input id="capacity-${room.id}" name="capacidade" type="number" min="20" step="1" value="${room.capacidade}" required>
              </div>
              <div class="admin-field">
                <label for="status-${room.id}">StatusSala</label>
                <select id="status-${room.id}" name="statusSala" required>
                  ${ROOM_STATUSES.map(
                    (status) =>
                      `<option value="${status}"${safeStatus === status ? " selected" : ""}>${status}</option>`,
                  ).join("")}
                </select>
              </div>
              <button class="button button-quiet button-small" type="submit">Salvar alterações</button>
            </form>
          `;
        })
        .join("")}
    </div>
  `;

  const renderAdministrator = () => {
    const maintenanceCount = state.rooms.filter(
      (room) => room.statusSala === ROOM_STATUS.MAINTENANCE,
    ).length;
    content.innerHTML = `
      ${renderHeading(
        "CONTROLE DE RECURSOS",
        "Gerenciar salas",
        "Atualize a capacidade e a disponibilidade das salas cadastradas.",
      )}
      ${renderStats()}
      <div class="info-banner">
        <span class="info-icon" aria-hidden="true">i</span>
        <span>Uma sala em <strong>EM_MANUTENCAO</strong> fica indisponível para novas solicitações. No momento, ${maintenanceCount} ${maintenanceCount === 1 ? "sala está" : "salas estão"} em manutenção.</span>
      </div>
      <section aria-labelledby="admin-rooms-title">
        <div class="section-heading">
          <div>
            <h2 id="admin-rooms-title">Salas cadastradas</h2>
            <p>Os estados disponíveis correspondem ao enum StatusSala.</p>
          </div>
        </div>
        ${renderAdminRooms()}
      </section>
    `;
  };

  const render = () => {
    if (!state.currentUser) {
      return;
    }
    profileLabel.textContent = profileNames[state.profile];
    accountName.textContent = state.currentUser.name;
    const pending = state.reservations.filter(
      (reservation) => reservation.status === RESERVATION_STATUS.REQUESTED,
    ).length;
    pendingCount.textContent = String(pending);
    pendingCount.classList.toggle("has-count", pending > 0);
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
    render();
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

  const openReservationDialog = (roomId, reservationId = null) => {
    const room = getRoom(roomId);
    const reservation = reservationId ? getReservation(reservationId) : null;
    if (!room) {
      showToast("Não foi possível localizar esta sala.", true);
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
      document.getElementById("start-date").value = reservation.inicio.slice(0, 10);
      document.getElementById("start-time").value = formatTime(reservation.inicio);
      document.getElementById("end-date").value = reservation.fim.slice(0, 10);
      document.getElementById("end-time").value = formatTime(reservation.fim);
    }

    dialog.showModal();
  };

  const handleReservationSubmit = (event) => {
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
    if (!reservationId && !window.ReservationRules.canRequestRoom(room)) {
      formError.textContent = "Esta sala não está disponível para novas solicitações.";
      return;
    }

    const conflict = window.ReservationRules.hasScheduleConflict(
      state.reservations,
      roomId,
      nextStart,
      nextEnd,
      reservationId,
    );

    if (conflict) {
      formError.textContent = "O horário informado conflita com outra reserva desta sala.";
      return;
    }

    if (reservationId) {
      const reservation = getReservation(reservationId);
      if (!reservation || !ACTIVE_RESERVATION_STATUSES.has(reservation.status)) {
        formError.textContent = "Esta reserva não pode mais ser alterada.";
        return;
      }
      reservation.inicio = nextStart;
      reservation.fim = nextEnd;
      showToast("Data e horário da reserva atualizados.");
    } else {
      const newId = `RSV-${Date.now().toString().slice(-6)}`;
      state.reservations.unshift({
        id: newId,
        salaId: roomId,
        solicitante: "Você",
        inicio: nextStart,
        fim: nextEnd,
        status: RESERVATION_STATUS.REQUESTED,
      });
      room.statusSala = ROOM_STATUS.RESERVED;
      showToast(`Solicitação enviada para análise: Sala ${room.id}.`);
    }

    dialog.close();
    render();
  };

  const handleReservationDecision = (reservationId, decision) => {
    const reservation = getReservation(reservationId);
    const room = reservation ? getRoom(reservation.salaId) : null;
    if (!window.ReservationRules.decideReservation(reservation, decision, room)) {
      showToast("A solicitação não está disponível para decisão.", true);
      return;
    }
    updateRoomAvailability(reservation.salaId);
    showToast(
      decision === RESERVATION_STATUS.APPROVED
        ? `Solicitação ${reservation.id} aprovada.`
        : `Solicitação ${reservation.id} rejeitada.`,
    );
    render();
  };

  const cancelReservation = (reservationId) => {
    const reservation = getReservation(reservationId);
    if (!window.ReservationRules.cancelReservation(reservation)) {
      showToast("Esta reserva não pode mais ser cancelada.", true);
      return;
    }
    updateRoomAvailability(reservation.salaId);
    showToast(`Reserva ${reservation.id} cancelada.`);
    render();
  };

  const saveRoom = (form) => {
    const roomId = Number(form.dataset.adminRoom);
    const room = getRoom(roomId);
    const formData = new FormData(form);
    const capacity = Number(formData.get("capacidade"));
    const status = String(formData.get("statusSala"));
    if (!room || !Number.isInteger(capacity) || capacity < 20) {
      showToast("A capacidade deve ser um número inteiro de pelo menos 20 pessoas.", true);
      return;
    }
    if (!window.ReservationRules.isRoomStatus(status)) {
      showToast("Selecione um estado válido para a sala.", true);
      return;
    }

    room.capacidade = capacity;
    room.statusSala = status;
    showToast(
      status === ROOM_STATUS.MAINTENANCE
        ? `Sala ${room.id} em manutenção. Novas solicitações estão bloqueadas.`
        : `Alterações da Sala ${room.id} salvas.`,
    );
    render();
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
    }
  });

  content.addEventListener("input", (event) => {
    const target = event.target;
    if (target instanceof HTMLInputElement && target.id === "room-search") {
      state.searchTerm = target.value;
      renderRoomGrid();
    }
  });

  content.addEventListener("submit", (event) => {
    const target = event.target;
    if (target instanceof HTMLFormElement && target.matches("[data-admin-room]")) {
      event.preventDefault();
      saveRoom(target);
    }
  });

  reservationForm.addEventListener("submit", handleReservationSubmit);
  dialog.querySelectorAll("[data-close-dialog]").forEach((button) => {
    button.addEventListener("click", () => dialog.close());
  });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) {
      dialog.close();
    }
  });

  todayLabel.textContent = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  }).format(new Date());

})();
