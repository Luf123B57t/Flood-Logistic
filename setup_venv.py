"""
Cross-platform Python virtual environment setup script for Maven.
Works on Windows, macOS, and Linux.
"""
import sys
import subprocess
from pathlib import Path

def main():
    # Get project directory
    project_dir = Path.cwd()
    venv_dir = project_dir / "venv"
    requirements_file = project_dir / "requirements.txt"

    # Determine Python executable name based on OS
    if sys.platform == "win32":
        python_exe = venv_dir / "Scripts" / "python.exe"
        pip_exe = venv_dir / "Scripts" / "pip.exe"
    else:
        python_exe = venv_dir / "bin" / "python"
        pip_exe = venv_dir / "bin" / "pip"

    # Check if venv already exists
    if venv_dir.exists() and python_exe.exists():
        print(f"Python venv already exists at {venv_dir} (skipping setup)")
        return 0

    print("=" * 67)
    print(f"Creating Python virtual environment at {venv_dir}...")
    print("=" * 67)

    # Create virtual environment
    try:
        subprocess.check_call([sys.executable, "-m", "venv", str(venv_dir)])
        print(f"Virtual environment created")
    except subprocess.CalledProcessError as e:
        print(f"Failed to create virtual environment: {e}")
        return 1

    # Upgrade pip
    print("\nUpgrading pip...")
    try:
        subprocess.check_call([str(python_exe), "-m", "pip", "install", "--upgrade", "pip"])
        print("Pip upgraded")
    except subprocess.CalledProcessError as e:
        print(f"Failed to upgrade pip: {e}")
        return 1

    # Install requirements
    if requirements_file.exists():
        print(f"\nInstalling requirements from {requirements_file.relative_to(project_dir)}...")
        try:
            subprocess.check_call([str(pip_exe), "install", "-r", str(requirements_file)])
            print("Requirements installed")
        except subprocess.CalledProcessError as e:
            print(f"Failed to install requirements: {e}")
            return 1
    else:
        print(f"Requirements file not found: {requirements_file}")

    print("=" * 67)
    print(f"Python venv setup complete at {venv_dir}")
    print("=" * 67)
    return 0

if __name__ == "__main__":
    sys.exit(main())

