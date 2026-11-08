export const sourceGroups = [
  { label: '知识库', types: ['PROVISION', 'CASE', 'VIEWPOINT', 'PAPER', 'QUESTION', 'CATALOG'] },
  { label: '我的学习', types: ['NOTE', 'FAVORITE', 'FOLDER', 'EXAM', 'WRONG', 'STATS', 'STUDY', 'CHAT'] },
  { label: '交流与个人中心', types: ['POST', 'MY_POST', 'FRIEND_POST', 'FRIEND', 'MEMBER', 'PROFILE'] }
]

export const sourceLabels = {
  PROVISION: '法条', CASE: '案例', VIEWPOINT: '学者观点与论文', PAPER: '仅论文', QUESTION: '题库', CATALOG: '知识库概况',
  NOTE: '学习笔记', FAVORITE: '我的收藏', FOLDER: '收藏分区', EXAM: '答题记录', WRONG: '错题本',
  STATS: '学习统计', STUDY: '学习活动', CHAT: '问答记录', POST: '交流帖子与评论',
  MY_POST: '我的帖子', FRIEND_POST: '好友动态',
  FRIEND: '我的好友', MEMBER: '站内用户', PROFILE: '个人资料'
}

export function originalPage(source) {
  const { type, id } = source
  switch (type) {
    case 'PROVISION': return `/provisions/${id}`
    case 'CASE': return `/cases/${id}`
    case 'VIEWPOINT': return `/viewpoints/${id}`
    case 'POST': return `/community/posts/${id}`
    case 'PROFILE': return '/profile'
    case 'STATS': return '/stats'
    case 'EXAM':
      if (source.summary?.startsWith('进行中')) return `/exam/run/${id}`
      if (source.summary?.startsWith('已交卷')) return `/exam/result/${id}`
      return null
    case 'FRIEND':
    case 'MEMBER': return { path: '/community', query: { member: String(id) } }
    case 'FOLDER': return { path: '/profile', query: { tab: 'favorites', folder: String(id) } }
    default: return null
  }
}
