"""
Agent Skills System
===================

Skills are modular capabilities that agents can use.
Each skill provides specific functions and requires certain capabilities.
"""

from .base import Skill, SkillResult
from .loader import SkillLoader, get_skill, list_skills

__all__ = ["Skill", "SkillResult", "SkillLoader", "get_skill", "list_skills"]
