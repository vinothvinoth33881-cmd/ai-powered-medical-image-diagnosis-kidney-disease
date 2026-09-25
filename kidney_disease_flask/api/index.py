import os
import sys

# Ensure project root directory is on the Python module search path
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from app import app

# Vercel WSGI entry point
# Exports the Flask WSGI callable
application = app
