"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const projectRoot = path.resolve(__dirname, "..");
const rules = require(path.join(
  projectRoot,
  "src/main/resources/static/js/reservation-rules.js",
));

const readProjectFile = (relativePath) =>
  fs.readFileSync(path.join(projectRoot, relativePath), "utf8");

const enumValues = (relativePath, enumName) => {
  const source = readProjectFile(relativePath);
  const match = source.match(new RegExp(`enum\\s+${enumName}\\s*\\{([^}]*)\\}`));
  assert.ok(match, `Could not find ${enumName} in ${relativePath}`);
  return match[1]
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
};

test("frontend room states stay synchronized with the Spring Boot enum", () => {
  assert.deepEqual(
    rules.ROOM_STATUSES,
    enumValues(
      "src/main/java/br/com/projeto_qualidade_software/projeto/enums/StatusSala.java",
      "StatusSala",
    ),
  );
});

test("frontend reservation states stay synchronized with the Spring Boot enum", () => {
  assert.deepEqual(
    rules.RESERVATION_STATUSES,
    enumValues(
      "src/main/java/br/com/projeto_qualidade_software/projeto/enums/StatusReserva.java",
      "StatusReserva",
    ),
  );
});

test("only available rooms accept new requests", () => {
  for (const status of rules.ROOM_STATUSES) {
    assert.equal(
      rules.canRequestRoom({ statusSala: status }),
      status === rules.ROOM_STATUS.AVAILABLE,
    );
  }
  assert.equal(rules.canRequestRoom(null), false);
});

test("responsible can decide only requested reservations", () => {
  const reservation = { status: rules.RESERVATION_STATUS.REQUESTED };
  assert.equal(
    rules.decideReservation(reservation, rules.RESERVATION_STATUS.APPROVED, {
      statusSala: rules.ROOM_STATUS.RESERVED,
    }),
    true,
  );
  assert.equal(reservation.status, rules.RESERVATION_STATUS.APPROVED);
  assert.equal(
    rules.decideReservation(reservation, rules.RESERVATION_STATUS.REJECTED, {
      statusSala: rules.ROOM_STATUS.AVAILABLE,
    }),
    false,
  );
});

test("responsible cannot approve a request for a room under maintenance", () => {
  const reservation = { status: rules.RESERVATION_STATUS.REQUESTED };
  assert.equal(
    rules.decideReservation(reservation, rules.RESERVATION_STATUS.APPROVED, {
      statusSala: rules.ROOM_STATUS.MAINTENANCE,
    }),
    false,
  );
  assert.equal(reservation.status, rules.RESERVATION_STATUS.REQUESTED);
  assert.equal(
    rules.decideReservation(reservation, rules.RESERVATION_STATUS.REJECTED, {
      statusSala: rules.ROOM_STATUS.MAINTENANCE,
    }),
    true,
  );
});

test("cancellation is limited to requested or approved reservations", () => {
  const requested = { status: rules.RESERVATION_STATUS.REQUESTED };
  const rejected = { status: rules.RESERVATION_STATUS.REJECTED };
  assert.equal(rules.cancelReservation(requested), true);
  assert.equal(requested.status, rules.RESERVATION_STATUS.CANCELLED);
  assert.equal(rules.cancelReservation(rejected), false);
});

test("reservation intervals require a valid end after the start", () => {
  assert.equal(
    rules.isValidInterval("2026-10-16T09:00", "2026-10-16T10:00"),
    true,
  );
  assert.equal(
    rules.isValidInterval("2026-10-16T10:00", "2026-10-16T10:00"),
    false,
  );
  assert.equal(
    rules.isValidInterval("2026-10-16T11:00", "2026-10-16T10:00"),
    false,
  );
  assert.equal(rules.isValidInterval("", "2026-10-16T10:00"), false);
});

test("schedule conflicts reject overlap but allow adjacent reservations", () => {
  const reservations = [
    {
      id: "RSV-1",
      salaId: 101,
      inicio: "2026-10-16T09:00",
      fim: "2026-10-16T10:00",
      status: rules.RESERVATION_STATUS.APPROVED,
    },
    {
      id: "RSV-2",
      salaId: 101,
      inicio: "2026-10-16T12:00",
      fim: "2026-10-16T13:00",
      status: rules.RESERVATION_STATUS.CANCELLED,
    },
  ];

  assert.equal(
    rules.hasScheduleConflict(
      reservations,
      101,
      "2026-10-16T09:30",
      "2026-10-16T10:30",
    ),
    true,
  );
  assert.equal(
    rules.hasScheduleConflict(
      reservations,
      101,
      "2026-10-16T10:00",
      "2026-10-16T11:00",
    ),
    false,
  );
  assert.equal(
    rules.hasScheduleConflict(
      reservations,
      204,
      "2026-10-16T09:30",
      "2026-10-16T10:30",
    ),
    false,
  );
  assert.equal(
    rules.hasScheduleConflict(
      reservations,
      101,
      "2026-10-16T09:30",
      "2026-10-16T10:30",
      "RSV-1",
    ),
    false,
  );
});

test("room maintenance stays blocked while other room states follow active reservations", () => {
  const activeRequest = {
    salaId: 101,
    status: rules.RESERVATION_STATUS.REQUESTED,
  };
  const cancelledRequest = {
    salaId: 204,
    status: rules.RESERVATION_STATUS.CANCELLED,
  };

  assert.equal(
    rules.getRoomAvailability(
      rules.ROOM_STATUS.MAINTENANCE,
      [activeRequest],
      101,
    ),
    rules.ROOM_STATUS.MAINTENANCE,
  );
  assert.equal(
    rules.getRoomAvailability(
      rules.ROOM_STATUS.AVAILABLE,
      [activeRequest],
      101,
    ),
    rules.ROOM_STATUS.RESERVED,
  );
  assert.equal(
    rules.getRoomAvailability(
      rules.ROOM_STATUS.RESERVED,
      [cancelledRequest],
      204,
    ),
    rules.ROOM_STATUS.AVAILABLE,
  );
});

test("Spring Boot static entry point loads local assets without backend calls", () => {
  const html = readProjectFile("src/main/resources/static/index.html");
  const app = readProjectFile("src/main/resources/static/js/app.js");
  const styles = readProjectFile("src/main/resources/static/css/app.css");

  assert.match(html, /css\/app\.css/);
  assert.match(html, /js\/reservation-rules\.js/);
  assert.match(html, /js\/app\.js/);
  assert.match(html, /data-profile="solicitante"/);
  assert.match(html, /data-profile="responsavel"/);
  assert.match(html, /data-profile="administrador"/);
  assert.match(app, /EM_MANUTENCAO/);
  assert.match(app, /approve-reservation/);
  assert.match(app, /reject-reservation/);
  assert.match(app, /edit-reservation/);
  assert.match(app, /cancel-reservation/);
  assert.doesNotMatch(app, /\bfetch\s*\(|XMLHttpRequest/);
  assert.match(styles, /@media \(max-width: 760px\)/);
});

test("login exposes only the work areas permitted for each mock role", () => {
  const html = readProjectFile("src/main/resources/static/index.html");
  const app = readProjectFile("src/main/resources/static/js/app.js");

  assert.match(html, /id="login-screen"/);
  assert.match(html, /id="login-form"/);
  assert.match(html, /id="logout-button"/);
  assert.match(app, /email: "solicitante@senac\.local"/);
  assert.match(app, /email: "responsavel@senac\.local"/);
  assert.match(app, /email: "admin@senac\.local"/);
  assert.match(app, /solicitante:\s*\["solicitante"\]/);
  assert.match(app, /responsavel:\s*\["responsavel"\]/);
  assert.match(app, /administrador:\s*\["administrador",\s*"responsavel"\]/);
  assert.match(app, /allowedProfiles\[state\.currentUser\.role\]\.includes\(profile\)/);
});
