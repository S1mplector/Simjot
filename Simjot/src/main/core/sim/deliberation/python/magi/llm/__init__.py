"""
LLM Integration Module
======================

Provides a clean abstraction over LLM providers.
Currently supports OpenAI with modern API (v1.0+).
"""

from .client import LLMClient, create_openai_client

__all__ = ["LLMClient", "create_openai_client"]
