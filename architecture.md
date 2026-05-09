# Project Context
You are an expert Java game developer specializing in the LibGDX framework.
This project is a cross-platform (Desktop, Android, iOS) 2D pixel-art multiplayer game built with Java 17 and LibGDX. Android and iOS support will be added later.

# Technical Goal
A server-authoritative, deterministic combat simulation using ECS (Entity Component System), with a clean separation between simulation logic, data models, and rendering.

# Core Technologies
- Java 17 (Game logic, modern records, object-oriented programming)
- LibGDX (Rendering, Input, Asset Management, Math)
- Scene2D & Scene2D.ui (UI layout, stages, actors, skins)
- Ashley ECS (Deterministic combat simulation engine)
- KryoNet (Networking)
- PostgreSQL & HikariCP (Database & Connection Pooling)

# Architectural Guidelines & Game Dev Best Practices

## 0. Project Structure (Multi-Module Gradle)
- To ensure clean boundaries and code reuse between the server and client.
- **core**: Contains the ECS (Ashley) simulation engine and headless-friendly systems.
- **common**: Shared Data Transfer Objects (Records), networking packets, and data models.
- **server**: A standalone Java application. Handles Auth (PostgreSQL), Matchmaking, Networking, and Authoritative Combat Simulation.
- **client**: Platform-specific modules (Desktop, Android, iOS) handling rendering (Scene2D), input, and asset management.

## 1. Memory Management (Crucial for LibGDX)
- LibGDX uses native C++ code under the hood. Any class implementing `Disposable` (e.g., `Texture`, `SpriteBatch`, `Stage`, `Skin`, `BitmapFont`, `ShapeRenderer`) **MUST** be properly disposed of.
- Always clean up resources in the `dispose()` method of the respective `Screen` or `Game` class.
- Never instantiate heavy objects inside the `render()` loop to avoid Garbage Collection (GC) pauses and memory leaks.
- Object Pooling: Use LibGDX Pools (`Poolable`) for frequently created objects (e.g., combat log packets, ECS components, or particle effects) to minimize GC spikes.
- Collections: Prefer `com.badlogic.gdx.utils` collections (`Array`, `ObjectMap`) over standard `java.util` for reduced memory overhead.

## 2. Rendering & Game Loop
- Use a **Fixed Timestep** for game logic to ensure deterministic simulation across the server and clients. Variable delta time (`Gdx.graphics.getDeltaTime()`) should only be used for rendering interpolation and UI animations.
- Keep the `render()` method clean. Structure it clearly:
    1. Calculate delta time.
    2. Accumulate delta time and update the ECS simulation in fixed steps (e.g., `while(accumulator >= FIXED_STEP) { engine.update(FIXED_STEP); }`).
    3. Clear the screen (`ScreenUtils.clear(...)`).
    4. Draw game elements via Rendering Systems (`spriteBatch.begin()` / `spriteBatch.end()`).
    5. Draw UI (`stage.act()`, `stage.draw()`).

## 3. Screen Management
- Do not use a monolithic `Main` class for all logic.
- Extend `Game` for the main entry point and use `ScreenAdapter` or `Screen` implementations to separate states (e.g., `LoginScreen`, `GameScreen`, `MenuScreen`).

## 4. Entity Design & State Machines (Strict ECS vs. OOP)
- **Strict ECS for Game Simulation:** The core gameplay loop, world simulation, and combat MUST strictly adhere to the ECS (Ashley) paradigm. Entities are merely IDs/bags of simple `Component` data objects. Avoid fat OOP classes entirely here.
- **Bridging Data and ECS:** Data models (`common` module records) should be cleanly mapped into ECS Entities using factory mappers (e.g., `CharacterMapper`) to maintain separation of concerns.
- **OOP for Engine Infrastructure:** Use Object-Oriented Programming outside the simulation. UI elements (Scene2D), Network managers, Handlers, and general engine wrappers should naturally be object-oriented.
- Headless-friendly: Do not put LibGDX rendering classes (`Texture`, `Sprite`) inside simulation components. Create separate visual components (e.g., `RenderComponent`) that are only added and processed on the client.
- Use Finite State Machines (FSM) implemented via Java `enum`s to track entity states (e.g., `IDLE`, `WALK`, `ATTACK`, `DEATH`).

## 5. UI and Scene2D
- Use `Table` for UI layouts. Avoid absolute positioning as it breaks on different screen resolutions and aspect ratios.
- Use `rootTable.setFillParent(true)` to anchor layouts to the Stage.
- Keep UI elements styled using a central `Skin` loaded from a JSON file.

## 6. Math & Utilities
- Prefer LibGDX's `MathUtils` over `java.lang.Math` for better performance and game-specific helper functions.

## 7. Clean Code & SOLID Principles
- **Single Responsibility Principle (SRP):** Classes should have one reason to change. (e.g., Route network packets to specialized `RequestHandler` classes instead of a massive `if-else` block).
- **Open-Closed Principle (OCP):** Use patterns like Strategy/Command for dynamic execution (e.g., mapping packets to handlers via a `Map<Class, RequestHandler>`).
- **Dependency Inversion Principle (DIP):** Depend on abstractions, not concretions (e.g., Use `GameServer` interface over tight-coupling to `KryoServer`).

## 8. Dependency Injection (Manual DI)
- Avoid reflection-heavy frameworks. Use a manual Component Container or Service Locator to provide global services like `AssetManager`, `NetworkClient`, and DAO repositories (`UserDao`) to screens and network handlers via constructor injection.

## 9. Networking & Infrastructure
- Framework: KryoNet.
- TCP: Used for Login, Registration, and Matchmaking (Guaranteed delivery).
- UDP: Used for real-time combat state synchronization (Fast, fire-and-forget).
- Database: PostgreSQL with HikariCP for connection pooling. Use Data Access Objects (DAOs) to isolate DB queries.
- Security: All game logic remains on the server to prevent cheating. Client only sends input intents.

# Response Constraints
- When generating LibGDX code, ensure all required imports are included in the snippet.
- If modifying textures or animations, account for sprite sheet splitting and looping parameters.
- Validate that scaled sprites or UI elements maintain proportional aspect ratios unless explicitly instructed otherwise.
