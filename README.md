# OOP Project - Social Media & News Analysis System

A JavaFX-based desktop application for scraping, storing, and analyzing social media posts and news articles using sentiment analysis and damage assessment algorithms.

## 📋 Project Overview

This application is a project demonstrating object-oriented programming principles through a comprehensive data analysis system. It combines web scraping, database persistence, sentiment analysis using machine learning, and a modern GUI interface.

### Key Features

- **Web Scraping**: Automated data collection from Facebook posts and newspaper articles using Selenium WebDriver
- **Data Persistence**: SQLite database storage with DAO pattern implementation
- **Sentiment Analysis**: Vietnamese text sentiment analysis using Hugging Face Transformers
- **Damage Assessment**: Specialized processors for analyzing disaster-related content
- **Interactive GUI**: JavaFX-based user interface with FXML layouts
- **Extensible Architecture**: Plugin-style processor and scraper system

## 🏗️ Architecture

The project follows a clean architecture pattern with clear separation of concerns:

```
├── domain/             # Business logic and domain models
│   ├── model/          # Core entities (Subject, Task, Instance)
│   ├── service/        # Analyzer service
│   ├── processor/      # Analysis processors (Strategy pattern)
│   └── enums/          # Task and Instance type registries
├── application/        # Application services
│   ├── controller/     # Main controller coordination
│   └── service/        # Subject management service
├── infrastructure/     # External concerns
│   ├── persistence/    # Database access (DAO pattern)
│   ├── scraping/       # Web scraping framework
│   ├── external/       # Python integration
│   └── config/         # Configuration
└── presentation/       # UI layer
    ├── controller/     # FXML controllers
    ├── navigation/     # View navigation
    └── config/         # GUI configuration
```

## 🛠️ Technologies

### Java Stack
- **Java 25**: Core programming language
- **JavaFX 25.0.1**: Desktop GUI framework
- **Maven**: Build and dependency management
- **SQLite 3.47.1.0**: Embedded database
- **Selenium 4.15.0**: Web scraping automation
- **JSoup 1.17.2**: HTML parsing
- **SLF4J/Logback**: Logging framework

### Python Stack
- **Python 3.13**: Sentiment analysis service
- **PyTorch 2.9.1**: Deep learning framework
- **Transformers 4.57.3**: Hugging Face NLP library
- **Model**: Vietnamese-Sentiment-visobert (5CD-AI)

## 📦 Prerequisites

- **Java Development Kit (JDK) 25** or higher
- **Python 3.13** or higher
- **Maven 3.8+**
- **ChromeDriver** (automatically managed by WebDriverManager)

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd oop
```

### 2. Set Up Python Virtual Environment

The project includes automated Python environment setup:

```bash
python3 setup_venv.py
```

Or manually:

```bash
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Build the Project

```bash
mvn clean install
```

This will:
- Compile Java sources
- Set up the Python virtual environment (if not already done)
- Download dependencies
- Run tests (if available)

## ▶️ Running the Application

### Using Maven

```bash
mvn clean javafx:run
```

### Using IDE

Run the `com.aidsight.Launcher` class as the main entry point.

## 📖 User Guide

### Creating a Subject

1. Launch the application
2. Click **"Add Subject"** on the home screen
3. Enter subject name and description
4. Select instance types to scrape (Facebook Posts, Newspaper Articles)
5. Enter URLs for scraping
6. Select analysis tasks (Sentiment Tracking, Damage Assessment)
7. Save the subject

### Analyzing Data

1. Select a subject from the home screen
2. Click **"Analyze"**
3. The system will:
   - Scrape data from configured URLs
   - Store instances in the database
   - Run selected analysis tasks
   - Display aggregated results

### Viewing Results

Analysis results are displayed in charts and tables showing:
- Sentiment distribution (Positive, Negative, Neutral)
- Damage assessment metrics
- Temporal trends
- Instance-level details

## 🔌 Extending the System

### Adding a New Instance Type

1. Create a new instance class extending `Instance`
2. Add entry to `InstanceType` enum with:
   - Instance class reference
   - Display name
   - Scraper factory (if applicable)
   - DAO factory
3. Implement the corresponding DAO and Scraper

### Adding a New Task Type

1. Create a new task class extending `Task`
2. Define the result class extending `Task.Result`
3. Add entry to `TaskType` enum with:
   - Task factory
   - Display name and description
   - Identifier
4. Register processors in `ProcessorType` enum for each supported instance type

### Adding a New Processor

1. Implement the `Processor<I extends Instance, T extends Task>` interface
2. Register in `ProcessorType` enum for the task-instance combination
3. Implement the `process()` method with analysis logic

## 📁 Project Structure

```
oop/
├── src/
│   ├── main/
│   │   ├── java/           # Java source code
│   │   ├── python/         # Python sentiment analysis service
│   │   └── resources/      # FXML layouts, CSS, configs
│   └── test/               # Test sources
├── database/               # SQLite database files
├── logs/                   # Application logs
├── pom.xml                 # Maven configuration
├── requirements.txt        # Python dependencies
└── README.md              # This file
```

## 🗄️ Database Schema

The application uses SQLite with the following main tables:
- **subjects**: Subject definitions
- **tasks**: Task configurations per subject
- **instances**: Base instance data
- **facebook_posts**: Facebook-specific data
- **newspaper_articles**: Newspaper-specific data
- **analysis_results**: Cached analysis results

## 🔍 Key Design Patterns

- **DAO Pattern**: Database access abstraction
- **Strategy Pattern**: Pluggable processors for different task-instance combinations
- **Factory Pattern**: Instance and task creation
- **Registry Pattern**: Central type registries (TaskType, InstanceType, ProcessorType)
- **MVC Pattern**: Separation of model, view, and controller
- **Template Method**: Base classes for Instance and Task with extensible behavior

## 🐛 Troubleshooting


### Python Model Loading Issues

If sentiment analysis fails:
```bash
# Clear Hugging Face cache
rm -rf ~/.cache/huggingface/

# Reinstall dependencies
pip install --force-reinstall -r requirements.txt
```

### Web Scraping Issues

- Ensure ChromeDriver is compatible with your Chrome browser
- Check internet connectivity
- Verify URLs are accessible
- Review logs in `logs/application.log`

### Database Errors

- Check file permissions in `database/` directory
- Ensure SQLite driver is properly loaded
- Review database initialization logs

## 📝 Logging

Logs are stored in `logs/` directory:
- `application.log`: Current session logs
- `application.YYYY-MM-DD.log`: Archived daily logs

Configuration: `src/main/resources/logback.xml`

## 👥 Authors

- **Dao Minh Tam**
- **Dinh Tuan Minh**
- **Nguyen Duc Dung**
- **Nguyen Danh Bao**
- **Luu Hieu An**

## 📄 License

This project is created for educational purposes as part of a university course.

## 🙏 Acknowledgments

We would like to express our sincere gratitude to:

- **Dr. Trinh Tuan Dat** - Our instructor, for guidance and support throughout this project
- Vietnamese-Sentiment-visobert model by 5CD-AI
- OpenJFX community
- Selenium and WebDriver maintainers
- Hugging Face Transformers team

