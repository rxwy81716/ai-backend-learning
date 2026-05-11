import request from '@/utils/request'

export interface SkillDescriptor {
  name: string
  displayName: string
  description: string
  category: string
  version: string
  inputParams: string[]
  outputFormat: string
  beanClass: string
}

export interface SkillResult {
  success: boolean
  data: string
  format: string
}

/** 列出所有已注册的 Skill */
export function listSkills() {
  return request.get<any, SkillDescriptor[]>('/api/skills')
}

/** 获取指定 Skill 详情 */
export function getSkill(name: string) {
  return request.get<any, SkillDescriptor>(`/api/skills/${name}`)
}

/** 执行指定 Skill */
export function executeSkill(name: string, params: Record<string, any>) {
  return request.post<any, SkillResult>(`/api/skills/${name}/execute`, params)
}
